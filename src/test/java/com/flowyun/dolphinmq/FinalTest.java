package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.PullConsumerClient;
import com.flowyun.dolphinmq.consumer.SubscriptionData;
import com.flowyun.dolphinmq.consumer.TopicListener;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;

/**
 * 〈一句话功能简述〉
 * 〈功能详细描述〉
 *
 * @author Barry
 * @since 2021/7/6 15:09
 */
public class FinalTest {
    private RedissonClient redisson;
    private Logger logger;
    PullConsumerClient pullConsumerClient;

    @Test
    void test() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        redisson = Redisson.create(config);

        pullConsumerClient = new PullConsumerClient(
                redisson,
                "service"
        );
        SubscriptionData<Testbean> t1 = pullConsumerClient.subscribe("t1", Testbean.class);

        HiListener hiListener = new HiListener();

        t1.registerMessageListener(hiListener);
        t1.registerMessageListener(new TopicListener<Testbean>() {
            @Override
            public void consume(Testbean dto) {
                logger.info("dto:{}", dto);
            }
        });

        pullConsumerClient.start();
    }
}
