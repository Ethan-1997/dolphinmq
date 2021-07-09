package com.flowyun.dolphinmq.consumer;

import com.flowyun.dolphinmq.common.DolphinMQConfig;
import com.flowyun.dolphinmq.executor.CheckPendingListScheduledExecutor;
import com.flowyun.dolphinmq.executor.PullHealthyMessagesScheduledExecutor;
import com.flowyun.dolphinmq.utils.BeanMapUtils;
import io.netty.util.internal.StringUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.client.RedisBusyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 以拉取方式进行消费的消费者
 *
 * @author Barry
 * @since 2021/6/28 16:12
 */
@Slf4j
@Getter
@Setter
@Component
public class PullConsumerClient {
    private RedissonClient client;
    private RStream<Object, Object> deadStream;
    private String consumerGroup;
    private String consumer;
    private DolphinMQConfig config;
    static PullConsumerClient pullConsumerClient;
    Set<Subscriber<?>> subscriptions;

    private static String DEAD_STREAM_NAME = "DeadStream";

    @Autowired
    public void setConfig(DolphinMQConfig config) {
        this.config = config;
    }

    @Autowired
    public void setPullConsumerClient(PullConsumerClient client) {
        pullConsumerClient = client;
    }

    /**
     * 订阅主题
     *
     * @param topic 主题名
     * @return 返回SubscriptionData
     * @author Barry
     * @since 2021/7/6 9:56
     */
    public <T> Subscriber<T> subscribe(String topic) {
        Subscriber<T> subscriber = new Subscriber<>(topic, client, this);
        subscriptions.add(subscriber);
        return subscriber;
    }

    /**
     * 初始化消费者，默认消费者格式为：PC-201309011313/122.206.73.83
     *
     * @author Barry
     * @since 2021/6/28 16:23
     **/
   /* public PullConsumerClient(RedissonClient client, String consumerGroup) {
        this.client = client;
        this.consumerGroup = consumerGroup;
    }*/
    private PullConsumerClient() {

    }


    /**
     * 检查PendingList(进行消费偶尔失败、消费一直失败、死信情况处理)
     *
     * @author Barry
     * @since 2021/6/28 17:11
     **/
    public void checkPendingList() {
        for (Subscriber<?>
                subscriber :
                subscriptions) {
            RStream<Object, Object> stream = subscriber.getStream();
            RFuture<List<PendingEntry>> future = stream.listPendingAsync(
                    consumerGroup,
                    consumer,
                    StreamMessageId.MIN,
                    StreamMessageId.MAX,
                    config.getPendingListIdleThreshold(),
                    TimeUnit.SECONDS,
                    config.getCheckPendingListSize());
            future.thenAccept(pendingEntryList -> {

                Set<StreamMessageId> deadLetterIds = new HashSet<>();
                Set<StreamMessageId> idleIds = new HashSet<>();
                for (PendingEntry entry :
                        pendingEntryList) {
                    long cnt = entry.getLastTimeDelivered();
                    if (cnt >= this.config.getDeadLetterThreshold()) {
                        deadLetterIds.add(entry.getId());
                    } else {
                        idleIds.add(entry.getId());
                    }
                }
                consumeIdleMessages(idleIds, subscriber);
                consumeDeadLetterMessages(deadLetterIds, stream);
                claimIdleConsumer(stream);
            }).exceptionally(exception -> {
                exception.printStackTrace();
                return null;
            });
        }

    }

    /**
     * 认领空闲过久的消息
     *
     * @author Barry
     * @since 2021/7/5 16:44
     **/
    public void claimIdleConsumer(RStream<Object, Object> stream) {
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
                    config.getClaimThreshold(),
                    TimeUnit.MILLISECONDS,
                    config.getCheckPendingListSize());
            future.thenAccept(pendingEntryList -> {
                List<PendingEntry> pendingEntries = pendingEntryList.stream()
                        .filter(entry -> entry.getLastTimeDelivered() >= config.getDeadLetterThreshold())
                        .collect(Collectors.toList());
                String randConsumerName = getRandConsumerName(consumerNames);
                claim(pendingEntries, randConsumerName, stream);
            }).exceptionally(exception -> {
                log.info("listPendingAsync Error:{}", exception.getMessage());
                return null;
            });

        }).exceptionally(ex -> {
            log.info("Claim Error:{}", ex.getMessage());
            return null;
        });
    }

    private void claim(List<PendingEntry> pendingEntries, String randConsumerName, RStream<Object, Object> stream) {
        for (PendingEntry entry :
                pendingEntries) {
            StreamMessageId id = entry.getId();
            stream.claimAsync(consumerGroup, randConsumerName, config.getClaimThreshold(), TimeUnit.MILLISECONDS, id, id);
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
        for (Subscriber<?> subscriber :
                this.subscriptions) {
            RStream<Object, Object> stream = subscriber.getStream();
            RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                    stream.readGroupAsync(consumerGroup, consumer, config.getFetchMessageSize(), StreamMessageId.NEVER_DELIVERED);
            future.thenAccept(res -> consumeMessages(res, subscriber)).exceptionally(exception -> {
                log.info("consumeHealthMessages Exception:{}", exception.getMessage());
                exception.printStackTrace();
                return null;
            });
        }
    }

    /**
     * 消费空闲超时信息进行重传
     *
     * @param idleIds 超时列表
     * @author Barry
     * @since 2021/6/28 18:36
     **/
    private void consumeIdleMessages(Set<StreamMessageId> idleIds, Subscriber<?> data) {
        if (idleIds == null || idleIds.size() == 0) {
            return;
        }
        RStream<Object, Object> stream = data.getStream();
        RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                stream.readGroupAsync(consumerGroup, consumer, StreamMessageId.ALL);
        future.thenAccept(res -> {
            Map<StreamMessageId, Map<Object, Object>> messages = res.entrySet().stream().
                    filter(row -> idleIds.contains(row.getKey())).
                    collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            consumeMessages(messages, data);
        }).exceptionally(exception -> {
            log.info(exception.getMessage());
            return null;
        });
    }

    /**
     * 检查消费一直消费失败的信息（达到最大重试次数后会加入死信队列、通知管理员）
     *
     * @param deadLetterIds 死信ID列表
     * @author Barry
     * @since 2021/6/29 11:06
     */
    private void consumeDeadLetterMessages(Set<StreamMessageId> deadLetterIds, RStream<Object, Object> stream) {
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
                        exception.printStackTrace();
                        return null;
                    });
                }
            }).exceptionally(exception -> {
                exception.printStackTrace();
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
    private void consumeMessages(Map<StreamMessageId, Map<Object, Object>> res, Subscriber<?> data) {
        for (Map.Entry<StreamMessageId, Map<Object, Object>> entry :
                res.entrySet()) {
            consumeMessage(entry.getKey(), entry.getValue(), (Subscriber<Object>) data);
        }
    }

    /**
     * 消费单条数据
     * 判重(一般消费者需要根据业务ID做判重表，消息过的就不再消费消费等幂性存在Redis中进行查重)
     * 分布式锁 保证查看、消费、删除的原子性
     * todo 优化：只需要对不幂等的操作加锁，不用全部加
     *
     * @param id     消息ID
     * @param dtoMap Map格式数据
     * @author Barry
     * @since 2021/6/28 17:09
     **/
    public void consumeMessage(StreamMessageId id, Map<Object, Object> dtoMap, Subscriber<Object> subscriber) {
        RStream<Object, Object> stream = subscriber.getStream();
        String lockName = consumerGroup + id.toString();
        RLock lock = client.getLock(lockName);
        try {
            RFuture<Boolean> tryAsync = lock.tryLockAsync(100, 10, TimeUnit.SECONDS);
            tryAsync.thenAccept(tmp -> {
                RBucket<String> bucket = client.getBucket("bucket" + lockName);
                RFuture<String> bucketAsync = bucket.getAsync();
                bucketAsync.thenAccept(bucketRes -> {
                    if (StringUtil.isNullOrEmpty(bucketRes)) {
                        try {
                            subscriber.notify(BeanMapUtils.toBean(subscriber.getMsgClass(), dtoMap));
                            stream.ackAsync(consumerGroup, id);
                            bucket.setAsync("consumed");
                            bucket.expireAsync(30, TimeUnit.MINUTES);
                        } catch (IntrospectionException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
                            e.printStackTrace();
                        }
                    }
                }).exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });

            }).exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });

        } finally {
            lock.unlockAsync();
        }
    }


    /**
     * 创建消费者组
     *
     * @param startFromHead 是否从头开始订阅
     * @author Barry
     * @since 2021/7/1 14:36
     **/
    private void createConsumerGroup(boolean startFromHead) {
        for (Subscriber<?> data :
                subscriptions) {
            RStream<Object, Object> stream = data.getStream();
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

    public void start() {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(16);
        service.scheduleAtFixedRate(
                new PullHealthyMessagesScheduledExecutor(this),
                1,
                config.getPullHealthyMessagesPeriod(),
                TimeUnit.SECONDS);
        service.scheduleAtFixedRate(
                new CheckPendingListScheduledExecutor(this),
                1,
                config.getCheckPendingListsPeriod(),
                TimeUnit.SECONDS);

    }

    public static class Builder {

        public Builder() {
            
        }

        public Builder setRedissonClient(RedissonClient client) {
            pullConsumerClient.client = client;
            return this;
        }

        public Builder setService(String service) {
            pullConsumerClient.consumerGroup = service;
            return this;
        }

        public PullConsumerClient build() {
            try {
                pullConsumerClient.consumer = InetAddress.getLocalHost().toString();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            pullConsumerClient.subscriptions = new HashSet<>();
            pullConsumerClient.createConsumerGroup(pullConsumerClient.config.getIsStartFromHead());
            return pullConsumerClient;
        }
    }
}
