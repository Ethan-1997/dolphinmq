package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.Topic;
import org.junit.jupiter.api.Test;

/**
 * 〈一句话功能简述〉
 * 〈功能详细描述〉
 *
 * @author Barry
 * @since 2021/7/5 10:14
 */
public class TopicTest {
    @Test
    void test(){
        Topic<Testbean> topic = new Topic<>();
        new HiListener(topic);
        new Hi2Listener(topic);

        topic.setDto(new Testbean("123", 12));
    }

}
