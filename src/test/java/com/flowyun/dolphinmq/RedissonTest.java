package com.flowyun.dolphinmq;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.Redisson;
import org.redisson.api.RFuture;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamReadArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.api.stream.TrimStrategy;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

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
    void consumer() throws InterruptedException {
        RStream<String, String> stream = redisson.getStream("ttt");
//        stream.createGroup("testGroup",StreamMessageId.ALL);
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
}
