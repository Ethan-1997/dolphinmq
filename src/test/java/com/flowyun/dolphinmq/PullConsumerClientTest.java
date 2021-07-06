package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.PullConsumerClient;
import com.flowyun.dolphinmq.consumer.SubscriptionData;
import com.flowyun.dolphinmq.consumer.TopicListener;
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
public class PullConsumerClientTest {
    private RedissonClient redisson;
    private Logger logger;
    PullConsumerClient pullConsumerClient;

    @BeforeAll
    void config() {
        logger = LoggerFactory.getLogger(RedissonTest.class);
        // 1. Create config object
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        redisson = Redisson.create(config);

        pullConsumerClient = new PullConsumerClient(
                redisson,
                "service"
        );
        SubscriptionData<Testbean> t1 = pullConsumerClient.subscribe("t1",Testbean.class);

        HiListener hiListener = new HiListener();
        Hi2Listener hi2Listener = new Hi2Listener();
        t1.registerMessageListener(hiListener);
        t1.registerMessageListener(hi2Listener);
//        topic.attach();
    }

    @Test
    void consume() {
        pullConsumerClient.consumeHealthMessages();
        while (true) {
        }
    }

    @Test
    void checkPendingList() {
        pullConsumerClient.checkPendingList();
        while (true) {
        }
    }


}
