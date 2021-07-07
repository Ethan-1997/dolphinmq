package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.PullConsumerClient;
import com.flowyun.dolphinmq.consumer.Subscriber;
import com.flowyun.dolphinmq.consumer.MsgListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * 〈一句话功能简述〉
 * 〈功能详细描述〉
 *
 * @author Barry
 * @since 2021/7/6 15:09
 */
@Slf4j
public class FinalTest {
    private RedissonClient redisson;
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
        Subscriber<Testbean> t1 = pullConsumerClient.subscribe("t1");

        HiListener hiListener = new HiListener();

        t1.registerListener(hiListener);
        t1.registerListener(new MsgListener<Testbean>() {
            @Override
            public void consume(Testbean dto) {
                log.info("dto:{}", dto);
            }
        });

        pullConsumerClient.start();
        while (true){

        }
    }
}
