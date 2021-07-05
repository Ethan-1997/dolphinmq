package com.flowyun.dolphinmq;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.api.stream.TrimStrategy;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *通用：
 * 1.重试机制 Redission
 * 2.日志：采用不同级别的日志，避免系统成为黑盒
 * 3.配置：自定义配置
 * producer
 * 1.创建或获取Topic
 *  1.1 重复生产消息 redis stream 不会接受一个重复的ID
 * 2.生成ID (优化的雪花算法)
 * 3.生产消息（异步）
 *  3.1 消息重投 重试机制 Redission
 * consumer
 * 性能：
 *  1. 批量拉取逐个消费
 *
 * 1.定时获取消息
 *  1.1 重复消费问题(消息成功消费后由于各种问题没能ACK成功) 消息唯一ID实现
 *  1.2 消费者流控，因为消费能力达到瓶颈。（生产速度远大于消费速度，导致消费堆积）
 *  1.3 consumer 从待处理消息列表开始消费（处理consumer宕机问题）（拉了消息还没消费就Crash）
 *  1.4 认领不活跃的消费者的消息（处理consumer永久宕机问题）（完全不来拉消息了）消费者空闲时间实现
 *  1.5 死信队列（达到最大重试次数后会加入死信队列）并通知管理员。（来拉消息但一直没有消费成功）消息计数器实现
 * 2.消费消息
 * 3.ACK
 */

/**
 * 进行Redission stream相关测试
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedissonTest {
    private RedissonClient redisson;
    private Logger logger;

    @BeforeAll
    void config() throws IOException {
        logger = LoggerFactory.getLogger(RedissonTest.class);
        // 1. Create config object
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        redisson = Redisson.create(config);
    }

    @Test
    void producer() {
        RStream<String, String> stream = redisson.getStream("ttt");
        RFuture<StreamMessageId> id1Future = stream.addAsync(StreamAddArgs.entry("test5", "test1").trim(TrimStrategy.MAXLEN, 1000));
        RFuture<StreamMessageId> id2Future = stream.addAsync(StreamAddArgs.entry("test6", "test2").trim(TrimStrategy.MAXLEN, 1000));
    }
    @Test
    void createGroup(){
        RStream<String, String> stream = redisson.getStream("ttt");
        stream.createGroup("testGroup",StreamMessageId.NEWEST);
    }

    @Test
    void consumer() throws InterruptedException {
        RStream<String, String> stream = redisson.getStream("ttt");
        RFuture<Map<StreamMessageId, Map<String, String>>> rFuture = stream.readGroupAsync(
                "testGroup",
                "test",
                StreamReadGroupArgs.greaterThan(StreamMessageId.NEVER_DELIVERED).count(1)
        );
        Thread.sleep(1000);

        rFuture.thenAccept(res -> {
            logger.info(res.toString());
            // 处理返回
            for (Map.Entry<StreamMessageId, Map<String, String>> entry :
                    res.entrySet()) {
                RFuture<Long> ackFuture = stream.ackAsync("testGroup", entry.getKey());
                ackFuture.thenAccept(res1 -> {
                    logger.info("ack success:" + res.toString());
                }).exceptionally(exception1 -> {
                    logger.info(exception1.getMessage());
                    return null;
                });
            }
        }).exceptionally(exception -> {
            logger.info(exception.getMessage());
            exception.printStackTrace();
            // 处理错误
            return null;
        });
    }
    @Test
    void TestPending(){
        RStream<Object, Object> stream = redisson.getStream("mystream");
        List<PendingEntry> entries = stream.listPending("mygroup", "alice", StreamMessageId.MIN, StreamMessageId.MAX, 10);
        logger.info(entries.get(0).toString());
    }

    @Test
    void testMap(){
        RMap<Object, Object> map = redisson.getMap("testMap");
        logger.info("get1:{}",map.get("test1"));
        map.put("test1",123);
        logger.info("get2:{}",map.get("test1"));
    }

    @Test
    void testLock(){
        RLock lock = redisson.getLock("anyLock");
// 最常见的使用方法
        boolean res = false;
        try {
            res = lock.tryLock(100, 10, TimeUnit.SECONDS);
            if (res) {
//                lock.unlock();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void tryGetLock(){
        RLock lock = redisson.getLock("anyLock");
// 最常见的使用方法
        try {
            lock.tryLock(100, 10, TimeUnit.SECONDS);
            logger.info("getLock!");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
