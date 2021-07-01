package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.PullConsumer;
import com.flowyun.dolphinmq.producer.Producer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Barry
 * @since 2021/6/30 19:52
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PullConsumerTest {
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
    void consume() {
        PullConsumer<Testbean> pullConsumer = new PullConsumer<Testbean>(
                redisson,
                "pullTest",
                "pullgroup",
                null,
                Testbean.class) {
            @Override
            public void consume(Testbean dto) {
                logger.info("dto:{}", dto.toString());
            }
        };
    }

}
