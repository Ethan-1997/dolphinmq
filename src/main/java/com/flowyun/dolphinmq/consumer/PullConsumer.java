package com.flowyun.dolphinmq.consumer;

import com.flowyun.dolphinmq.utils.BeanMapUtils;
import io.netty.util.internal.StringUtil;
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
import java.util.concurrent.BlockingDeque;
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
public class PullConsumer<T> {
    private RedissonClient client;
    private RStream<Object, Object> stream;
    private RStream<Object, Object> deadStream;
    private String consumerGroup;
    private String consumer;
    private String topicName;
    RMap<Object, Object> checkDuplicateMap;
    private Class dtoClass;
    private Topic<T> topic;

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
    public PullConsumer(RedissonClient client, String topicName, String consumerGroup, Class dtoClass) {
        this.client = client;
        this.consumerGroup = consumerGroup;
        try {
            this.consumer = InetAddress.getLocalHost().toString();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        this.topicName = topicName;
        this.dtoClass = dtoClass;
        this.fetchMessageSize = 5;
        this.pendingListIdleThreshold = 60;
        this.checkPendingListSize = 1000;
        this.deadLetterThreshold = 17;
        this.claimThreshold = 10;
        initStream();
        createConsumerGroup(true);
        topic = new Topic<>();
    }

    private void initStream() {
        stream = client.getStream(topicName);
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
            Set<StreamMessageId> idleIds = new HashSet<>();
            for (PendingEntry entry :
                    pendingEntryList) {
                long cnt = entry.getLastTimeDelivered();
                if (cnt >= this.deadLetterThreshold) {
                    deadLetterIds.add(entry.getId());
                } else {
                    idleIds.add(entry.getId());
                }
            }
            consumeIdleMessages(idleIds);
            consumeDeadLetterMessages(deadLetterIds);
            ClaimIdleConsumer();
        }).exceptionally(exception -> {
            return null;
        });
    }

    /**
     * 认领空闲过久的消息
     *
     * @author Barry
     * @since 2021/7/5 16:44
     **/
    public void ClaimIdleConsumer() {
        RFuture<PendingResult> infoAsync = stream.getPendingInfoAsync(consumerGroup);
        infoAsync.thenAccept(res -> {
            Map<String, Long> consumerNames = res.getConsumerNames();
            if (consumerNames.size() <= 1) {
                return;
            }

            RFuture<List<PendingEntry>> future = stream.listPendingAsync(
                    consumerGroup,
                    consumer,
                    StreamMessageId.MIN,
                    StreamMessageId.MAX,
                    claimThreshold,
                    TimeUnit.MILLISECONDS,
                    checkPendingListSize);
            future.thenAccept(pendingEntryList -> {
                List<PendingEntry> pendingEntries = pendingEntryList.stream()
                        .filter(entry -> entry.getLastTimeDelivered() >= this.deadLetterThreshold)
                        .collect(Collectors.toList());
                String randConsumerName = getRandConsumerName(consumerNames);
                claim(pendingEntries, randConsumerName);
            }).exceptionally(exception -> {
                log.info("listPendingAsync Error:{}", exception.getMessage());
                return null;
            });

        }).exceptionally(ex -> {
            log.info("Claim Error:{}", ex.getMessage());
            return null;
        });
    }

    private void claim(List<PendingEntry> pendingEntries, String randConsumerName) {
        for (PendingEntry entry :
                pendingEntries) {
            StreamMessageId id = entry.getId();
            stream.claimAsync(consumerGroup, randConsumerName, this.claimThreshold, TimeUnit.MILLISECONDS, id, id);
        }
    }

    private String getRandConsumerName(Map<String, Long> consumerNames) {
        List<Map.Entry<String, Long>> entries = consumerNames.entrySet().stream()
                .filter(entry -> entry.getKey().equals(consumer))
                .collect(Collectors.toList());

        Random rand = new Random();
        int i = rand.nextInt(entries.size());
        return entries.get(i).getKey();
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
     * 分布式锁 保证查看、消费、删除的原子性
     *
     * @param id     消息ID
     * @param dtoMap Map格式数据
     * @author Barry
     * @since 2021/6/28 17:09
     **/
    private void consumeMessage(StreamMessageId id, Map<Object, Object> dtoMap) {
        RLock lock = client.getLock(id.toString());
        try {
            RFuture<Boolean> tryAsync = lock.tryLockAsync(100, 10, TimeUnit.SECONDS);
            tryAsync.thenAccept(tmp -> {
                RBucket<String> bucket = client.getBucket(id.toString());
                if (StringUtil.isNullOrEmpty(bucket.get())) {
                    try {
                        topic.setDto((T) BeanMapUtils.toBean(dtoClass, dtoMap));
                        bucket.delete();
                    } catch (IntrospectionException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                }
            }).exceptionally(ex -> {
                return null;
            });

        } finally {
            lock.unlockAsync();
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
