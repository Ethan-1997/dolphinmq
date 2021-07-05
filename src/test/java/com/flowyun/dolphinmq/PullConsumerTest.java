package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.PullConsumer;
import com.flowyun.dolphinmq.consumer.Topic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Barry
 * @since 2021/6/30 19:52
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PullConsumerTest {
    private RedissonClient redisson;
    private Logger logger;
    PullConsumer<Testbean> pullConsumer;

    @BeforeAll
    void config() {
        logger = LoggerFactory.getLogger(RedissonTest.class);
        // 1. Create config object
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        redisson = Redisson.create(config);
        Topic<Testbean> topic = new Topic<>();
        pullConsumer = new PullConsumer<>(
                redisson,
                "t1",
                "service",
                Testbean.class);
        pullConsumer.setTopic(topic);

        new HiListener(topic);
        new Hi2Listener(topic);

//        topic.attach();
    }

    @Test
    void consume() {
        pullConsumer.consumeHealthMessages();
        while (true) {
        }
    }

    @Test
    void checkPendingList() {
        pullConsumer.checkPendingList();
        while (true) {
        }
    }



}
