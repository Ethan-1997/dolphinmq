package com.flowyun.dolphinmq.consumer;

import com.flowyun.dolphinmq.utils.BeanMapUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.api.listener.ListSetListener;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamMultiReadGroupArgs;
import org.redisson.api.stream.StreamReadArgs;
import org.redisson.api.stream.StreamReadGroupArgs;

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
public class PullConsumer {
    private RedissonClient client;
    private RStream<Object, Object> stream;
    private RStream<Object, Object> deadStream;
    private String consumerGroup;
    private String consumer;
    private String topic;
    RMap<Object, Object> checkDuplicateMap;

    //每次拉取数据的量
    private Integer fetchMessageSize;
    //检查consumer不活跃的门槛（单位秒）
    private Integer pendingListIdleThreshold;
    //每次拉取PendingList的大小
    private Integer checkPendingListSize;
    //死信计数器门槛
    private Integer deadLetterThreshold;
    private static String DEAD_STREAM_NAME = "DeadStream";

    public PullConsumer(RedissonClient client, String topic, String consumerGroup, String consumer, Integer fetchMessageSize) {
        this.client = client;
        this.consumerGroup = consumerGroup;
        this.consumer = consumer;
        this.topic = topic;
        this.fetchMessageSize = fetchMessageSize;
        this.pendingListIdleThreshold = 60;
        this.checkPendingListSize = 1000;
        this.deadLetterThreshold = 8;
        initStream();
    }

    private void initStream() {
        stream = client.getStream(topic);
    }

    /**
     * 初始化消费者，默认消费者格式为：PC-201309011313/122.206.73.83
     *
     * @param
     * @return
     * @throws
     * @author Barry
     * @since 2021/6/28 16:23
     **/
    public PullConsumer(RedissonClient client, String topic, String consumerGroup) throws UnknownHostException {
        this(client, topic, consumerGroup, InetAddress.getLocalHost().toString(), 5);
    }

    /**
     * 检查PendingList
     * <功能描述>
     *
     * @param
     * @return
     * @throws
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
     * @param
     * @return
     * @throws
     * @author Barry
     * @since 2021/6/28 17:08
     **/
    private void consumeHealthMessages() {
        RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                stream.readGroupAsync(consumerGroup, consumer, fetchMessageSize, StreamMessageId.NEVER_DELIVERED);
        future.thenAccept(res -> {
            consumeMessages(res);
        }).exceptionally(exception -> {
            return null;
        });
    }

    /**
     * 消费PendingList超时、宕机、丢失的消息
     *
     * @param
     * @param pendingEntryList
     * @return
     * @throws
     * @author Barry
     * @since 2021/6/28 18:36
     **/
    private void consumeTimeoutMessages(List<PendingEntry> pendingEntryList) {
        checkDuplicateMap = client.getMap(consumerGroup);

        if (pendingEntryList == null || pendingEntryList.size() == 0) {
            return;
        }
        List<StreamMessageId> streamMessageIdList = pendingEntryList.stream().map(ele -> ele.getId()).collect(Collectors.toList());
        //todo 这个进程只负责检查待处理消息列表，并将空闲的消息分配给看似活跃的消费者。XCLAIM
        // 可以通过Redis Stream的可观察特性获得活跃的消费者。
        RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                stream.readGroupAsync(consumerGroup, consumer, streamMessageIdList.toArray(new StreamMessageId[0]));
        future.thenAccept(res -> {
            consumeMessages(res);
        }).exceptionally(exception -> {
            return null;
        });
    }

    private void consumeMessages(Map<StreamMessageId, Map<Object, Object>> res) {
        checkDuplicateMap = client.getMap(consumerGroup);

        for (Map.Entry<StreamMessageId, Map<Object, Object>> entry :
                res.entrySet()) {
            //todo 123
            //consumeMessage(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 消费单条数据
     *
     * @param
     * @param id
     * @param dtoMap
     * @return
     * @throws
     * @author Barry
     * @since 2021/6/28 17:09
     **/
    private void consumeMessage(StreamMessageId id, Map<String, Object> dtoMap) {
        //todo classType从哪来？
        Integer dtoType = new Integer(1);
        /**
         *  判重(一般消费者需要根据业务ID做判重表，消息过的就不再消费消费等幂性存在Redis中进行查重)
         */
        //todo 消费成功后再redis中添加记录 采用HOOK实现
        if (checkDuplicateMap.containsKey(id.toString())) {
            return;
        }
        try {
            BeanMapUtils.toBean(dtoType.getClass(), dtoMap);
        } catch (IntrospectionException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        // Map<Object, Object>转DTO
        // BeanMapUtils.toBean(T.clas,dtoMap);
        // 消费


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
        consumeHealthMessages();//todo 定时进行消费
    }

    /**
     * 检查消费一直消费失败的信息（达到最大重试次数后会加入死信队列、通知管理员）
     *
     * @param
     * @return
     * @throws
     * @author Barry
     * @since 2021/6/29 11:06
     **/
    private void checkConsumeErrorMessages() {
        List<PendingEntry> entries = stream.listPending(consumerGroup, consumer, StreamMessageId.MIN, StreamMessageId.MAX, checkPendingListSize);
        List<PendingEntry> deadLetterEntries = entries.stream()
                .filter(entry -> entry.getLastTimeDelivered() >= deadLetterThreshold)
                .collect(Collectors.toList());

        deadStream = client.getStream(DEAD_STREAM_NAME);

        for (PendingEntry entry:
             deadLetterEntries) {
            Map<StreamMessageId, Map<Object, Object>> range = stream.range(entry.getId(), entry.getId());
            if (range != null && range.size() != 0) {
                Map<Object, Object> map = range.get(0);
                deadStream.add(entry.getId(),StreamAddArgs.entries(map));
                stream.remove(entry.getId());
            }
        }

        //todo 通知管理员

    }


}
