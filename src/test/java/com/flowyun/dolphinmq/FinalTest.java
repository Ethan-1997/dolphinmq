package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.PullConsumerClient;
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

    @Test
    void test() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        redisson = Redisson.create(config);

        HiListener<Testbean> hiListener = new HiListener<>();

        PullConsumerClient.builde()
                .setRedissonClient(redisson)
                .setService("service")
                .<Testbean>subscribe("t1")
                .registerListener(hiListener)
                .registerListener(hiListener)
                .<Testbean>subscribe("t2")
                .registerListener(hiListener)
                .start();
        while (true) {

        }
    }
}
