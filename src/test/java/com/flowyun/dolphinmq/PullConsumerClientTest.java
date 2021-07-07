package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.PullConsumerClient;
import com.flowyun.dolphinmq.consumer.Subscriber;
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
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.stream.Collectors;

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
        Subscriber<Testbean> t1 = pullConsumerClient.subscribe("t1");

        HiListener hiListener = new HiListener();
        Hi2Listener hi2Listener = new Hi2Listener();
        t1.registerListener(hiListener);
        t1.registerListener(hi2Listener);


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


    @Test
    void testIdempotent() {

        try {
            Map<Object, Object> hhhh = BeanMapUtils.toMap(new Testbean("hhhh", 23)).
                    entrySet().stream().collect(Collectors.toMap(o -> (Object) o, e -> e));
            pullConsumerClient.consumeMessage(
                    new StreamMessageId(336715923374l, 2l),
                    hhhh,
                    new Subscriber<Object>("t1", redisson));
        } catch (IntrospectionException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        while (true) {
        }

    }




}
