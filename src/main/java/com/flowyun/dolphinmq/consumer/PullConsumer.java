package com.flowyun.dolphinmq.consumer;

import com.flowyun.dolphinmq.utils.BeanMapUtils;
import jodd.util.StringUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.api.stream.StreamAddArgs;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 以拉取方式进行消费的消费者
 *
 * @author Barry
 * @since 2021/6/28 16:12
 */
@Slf4j
@Data
public abstract class PullConsumer<T> implements Consumer<T> {
    private RedissonClient client;
    private RStream<Object, Object> stream;
    private RStream<Object, Object> deadStream;
    private String consumerGroup;
    private String consumer;
    private String topic;
    RMap<Object, Object> checkDuplicateMap;
    private Class dtoClass;

    /**
     * 每次拉取数据的量
     */
    private Integer fetchMessageSize;
    /**
     * 检查consumer不活跃的门槛（单位秒）
     */
    private Integer pendingListIdleThreshold;
    /**
     * 每次拉取PendingList的大小
     */
    private Integer checkPendingListSize;
    /**
     * 死信计数器门槛
     */
    private Integer deadLetterThreshold;

    private static String DEAD_STREAM_NAME = "DeadStream";

    /**
     * 初始化消费者，默认消费者格式为：PC-201309011313/122.206.73.83
     *
     * @author Barry
     * @since 2021/6/28 16:23
     **/
    public PullConsumer(RedissonClient client, String topic, String consumerGroup, String consumer, Class dtoClass) {
        this.client = client;
        this.consumerGroup = consumerGroup;
        if (StringUtil.isEmpty(consumer)) {
            try {
                this.consumer= InetAddress.getLocalHost().toString();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        else{ this.consumer = consumer;}

        this.topic = topic;
        this.dtoClass = dtoClass;
        this.fetchMessageSize = 5;
        this.pendingListIdleThreshold = 60;
        this.checkPendingListSize = 1000;
        this.deadLetterThreshold = 8;
        initStream();
    }

    private void initStream() {
        stream = client.getStream(topic);
    }

    /**
     * 检查PendingList
     * <功能描述>
     *
     * @author Barry
     * @since 2021/6/28 17:11
     **/
    private void checkPendingList() {
        List<PendingEntry> pendingEntryList =
                stream.listPending(
                        consumerGroup,
                        consumer,
                        StreamMessageId.MIN,
                        StreamMessageId.MAX,
                        pendingListIdleThreshold,
                        TimeUnit.SECONDS,
                        checkPendingListSize);
        consumeTimeoutMessages(pendingEntryList);
    }


    /**
     * 正常消费fetchMessageSize条数据
     *
     * @author Barry
     * @since 2021/6/28 17:08
     **/
    private void consumeHealthMessages() {
        RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                stream.readGroupAsync(consumerGroup, consumer, fetchMessageSize, StreamMessageId.NEVER_DELIVERED);
        future.thenAccept(this::consumeMessages).exceptionally(exception -> {
            return null;
        });
    }

    /**
     * 消费PendingList超时、宕机、丢失的消息
     *
     * @param pendingEntryList 超时列表
     * @author Barry
     * @since 2021/6/28 18:36
     **/
    private void consumeTimeoutMessages(List<PendingEntry> pendingEntryList) {
        checkDuplicateMap = client.getMap(consumerGroup);

        if (pendingEntryList == null || pendingEntryList.size() == 0) {
            return;
        }
        //todo 这个线程只负责检查待处理消息列表，并将空闲的消息分配给看似活跃的消费者。XCLAIM
        // 可以通过Redis Stream的可观察特性获得活跃的消费者。
        RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                stream.readGroupAsync(consumerGroup, consumer, pendingEntryList.stream().map(PendingEntry::getId).toArray(StreamMessageId[]::new));
        future.thenAccept(this::consumeMessages).exceptionally(exception -> {
            return null;
        });
    }

    private void consumeMessages(Map<StreamMessageId, Map<Object, Object>> res) {
        checkDuplicateMap = client.getMap(consumerGroup);
        List<StreamMessageId> ackList = new ArrayList<>();
        for (Map.Entry<StreamMessageId, Map<Object, Object>> entry :
                res.entrySet()) {
            consumeMessage(entry.getKey(), entry.getValue());
            ackList.add(entry.getKey());
        }
        batchAck(ackList.toArray(new StreamMessageId[0]));
    }

    /**
     * 消费单条数据
     * 判重(一般消费者需要根据业务ID做判重表，消息过的就不再消费消费等幂性存在Redis中进行查重)
     * @param id     消息ID
     * @param dtoMap Map格式数据
     * @author Barry
     * @since 2021/6/28 17:09
     **/
    private void consumeMessage(StreamMessageId id, Map<Object, Object> dtoMap) {
        if (checkDuplicateMap.containsKey(id.toString())) {
            return;
        }
        try {
            consume((T)BeanMapUtils.toBean(dtoClass, dtoMap));
            checkDuplicateMap.put(id.toString(), null);
        } catch (IntrospectionException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }

    }


    /**
     * 批量ack
     *
     * @param ids id列表
     * @author Barry
     * @since 2021/6/28 17:06
     **/
    private void batchAck(StreamMessageId... ids) {
        stream.ack(consumerGroup, ids);
    }

    public void readMessagesAsync() {
        checkPendingList();//todo 定时器进行检查
        checkConsumeErrorMessages();//todo 定时器进行检查
        consumeHealthMessages();//todo 1.定时进行消费   2.间隔时间指数级增长 Pull长轮询优化
    }

    /**
     * 检查消费一直消费失败的信息（达到最大重试次数后会加入死信队列、通知管理员）
     *
     * @author Barry
     * @since 2021/6/29 11:06
     **/
    private void checkConsumeErrorMessages() {
        List<PendingEntry> entries = stream.listPending(consumerGroup, consumer, StreamMessageId.MIN, StreamMessageId.MAX, checkPendingListSize);
        List<PendingEntry> deadLetterEntries = entries.stream()
                .filter(entry -> entry.getLastTimeDelivered() >= deadLetterThreshold)
                .collect(Collectors.toList());

        deadStream = client.getStream(DEAD_STREAM_NAME);

        for (PendingEntry entry :
                deadLetterEntries) {
            Map<StreamMessageId, Map<Object, Object>> range = stream.range(entry.getId(), entry.getId());
            if (range != null && range.size() != 0) {
                Map<Object, Object> map = range.get(entry.getId());
                deadStream.add(entry.getId(), StreamAddArgs.entries(map));
                stream.remove(entry.getId());
            }
        }

        //todo 通知管理员

    }


}
