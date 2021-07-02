package com.flowyun.dolphinmq.consumer;

import com.flowyun.dolphinmq.utils.BeanMapUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.client.RedisBusyException;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
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
     * 死信门槛
     */
    private Integer deadLetterThreshold;
    /**
     * 认领门槛
     */
    private Integer claimThreshold;

    private static String DEAD_STREAM_NAME = "DeadStream";

    /**
     * 初始化消费者，默认消费者格式为：PC-201309011313/122.206.73.83
     *
     * @author Barry
     * @since 2021/6/28 16:23
     **/
    public PullConsumer(RedissonClient client, String topic, String consumerGroup, Class dtoClass) {
        this.client = client;
        this.consumerGroup = consumerGroup;
        try {
            this.consumer = InetAddress.getLocalHost().toString();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        this.topic = topic;
        this.dtoClass = dtoClass;
        this.fetchMessageSize = 5;
        this.pendingListIdleThreshold = 60;
        this.checkPendingListSize = 1000;
        this.deadLetterThreshold = 17;
        this.claimThreshold = 10;
        initStream();
        createConsumerGroup(true);
    }

    private void initStream() {
        stream = client.getStream(topic);
    }

    /**
     * 检查PendingList(进行消费偶尔失败、消费一直失败、死信情况处理)
     * todo 开一个线程专门负责
     *
     * @author Barry
     * @since 2021/6/28 17:11
     **/
    public void checkPendingList() {
        RFuture<List<PendingEntry>> future = stream.listPendingAsync(
                consumerGroup,
                consumer,
                StreamMessageId.MIN,
                StreamMessageId.MAX,
                pendingListIdleThreshold,
                TimeUnit.SECONDS,
                checkPendingListSize);
        future.thenAccept(pendingEntryList -> {

            Set<StreamMessageId> deadLetterIds = new HashSet<>();
            Set<StreamMessageId> claimIds = new HashSet<>();
            Set<StreamMessageId> idleIds = new HashSet<>();
            for (PendingEntry entry :
                    pendingEntryList) {
                long cnt = entry.getLastTimeDelivered();
                if (cnt >= this.deadLetterThreshold) {
                    deadLetterIds.add(entry.getId());
                } else if (cnt >= this.claimThreshold) {
                    claimIds.add(entry.getId());
                } else {
                    idleIds.add(entry.getId());
                }
            }
            consumeIdleMessages(idleIds);
            consumeDeadLetterMessages(deadLetterIds);
            //todo  consumeClaimIds
        }).exceptionally(exception -> {
            return null;
        });
    }

    /**
     * 正常消费fetchMessageSize条数据
     *
     * @author Barry
     * @since 2021/6/28 17:08
     **/
    public void consumeHealthMessages() {
        RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                stream.readGroupAsync(consumerGroup, consumer, fetchMessageSize, StreamMessageId.NEVER_DELIVERED);
        future.thenAccept(this::consumeMessages).exceptionally(exception -> {
            log.info("consumeHealthMessages Exception:{}", exception.getMessage());
            exception.printStackTrace();
            return null;
        });
    }

    /**
     * 消费空闲超时信息进行重传
     *
     * @param idleIds 超时列表
     * @author Barry
     * @since 2021/6/28 18:36
     **/
    private void consumeIdleMessages(Set<StreamMessageId> idleIds) {
        if (idleIds == null || idleIds.size() == 0) {
            return;
        }

//        checkDuplicateMap = client.getMap(consumerGroup); //todo 重复消费问题
        RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                stream.readGroupAsync(consumerGroup, consumer, StreamMessageId.ALL);
        future.thenAccept(res -> {
            Map<StreamMessageId, Map<Object, Object>> messages = res.entrySet().stream().
                    filter(row -> idleIds.contains(row.getKey())).
                    collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            consumeMessages(messages);
        }).exceptionally(exception -> {
            log.info(exception.getMessage());
            return null;
        });
    }

    /**
     * 检查消费一直消费失败的信息（达到最大重试次数后会加入死信队列、通知管理员）
     * //todo ack 并发优化
     *
     * @param deadLetterIds
     * @author Barry
     * @since 2021/6/29 11:06
     */
    private void consumeDeadLetterMessages(Set<StreamMessageId> deadLetterIds) {
        if (deadLetterIds == null || deadLetterIds.size() == 0) {
            return;
        }
        deadStream = client.getStream(DEAD_STREAM_NAME);
        for (StreamMessageId id :
                deadLetterIds) {
            RFuture<Map<StreamMessageId, Map<Object, Object>>> future = stream.rangeAsync(id, id);
            future.thenAccept(range -> {
                if (range != null && range.size() != 0) {
                    Map<Object, Object> map = range.get(id);
                    RFuture<Void> addAsync = deadStream.addAsync(StreamMessageId.AUTO_GENERATED, StreamAddArgs.entries(map));
                    addAsync.thenAccept(res -> {
                        stream.removeAsync(id);
                        stream.ackAsync(consumerGroup, id);
                    }).exceptionally(exception -> {
                        return null;
                    });
                }
            }).exceptionally(exception -> {
                return null;
            });
        }
        //todo 通知管理员

    }

    /**
     * 消费单条消息
     *
     * @param res 消息
     * @author Barry
     * @since 2021/7/2 11:39
     **/
    private void consumeMessages(Map<StreamMessageId, Map<Object, Object>> res) {
//        checkDuplicateMap = client.getMap(consumerGroup);
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
     *
     * @param id     消息ID
     * @param dtoMap Map格式数据
     * @author Barry
     * @since 2021/6/28 17:09
     **/
    private void consumeMessage(StreamMessageId id, Map<Object, Object> dtoMap) {
//        if (checkDuplicateMap.containsKey(id.toString())) {
//            return;
//        }
        try {
            consume((T) BeanMapUtils.toBean(dtoClass, dtoMap));
            //todo 设置 Map 的 Entry 过期时间
//            checkDuplicateMap.put(id.toString(), null);
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
        stream.ackAsync(consumerGroup, ids);
    }

    /**
     * 创建消费者组
     *
     * @param startFromHead 是否从头开始订阅
     * @author Barry
     * @since 2021/7/1 14:36
     **/
    private void createConsumerGroup(boolean startFromHead) {
        StreamMessageId id = StreamMessageId.NEWEST;
        if (startFromHead) {
            id = StreamMessageId.ALL;
        }
        try {
            stream.createGroupAsync(consumerGroup, id);
        } catch (RedisBusyException e) {
            log.info(e.getMessage());
        }
    }

}
