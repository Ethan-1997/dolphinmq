package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.common.Message;
import com.flowyun.dolphinmq.common.SequenceUtil;
import com.flowyun.dolphinmq.common.SnowflakeDistributeId;
import com.flowyun.dolphinmq.producer.Producer;
import com.flowyun.dolphinmq.utils.BeanMapUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * @author Barry
 * @since 2021/6/30 20:00
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProducerTest {
    public RedissonClient redisson;
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
    void produce() {
        Producer producer = new Producer(redisson);
        Message msg = new Message();
        Testbean test = new Testbean("test1", 12);
        msg.setTopic("producerTest2");
        try {
            msg.setProperties(BeanMapUtils.toMap(test));
        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        producer.sendMessageAsync(msg);
    }
}
