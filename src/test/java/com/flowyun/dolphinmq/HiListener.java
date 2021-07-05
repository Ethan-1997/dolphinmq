package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.Topic;
import com.flowyun.dolphinmq.consumer.TopicListener;
import lombok.extern.slf4j.Slf4j;

/**
 * HiListener
 *
 * @author Barry
 * @since 2021/7/5 10:07
 */
@Slf4j
public class HiListener extends TopicListener<Testbean> {

    public HiListener(Topic<Testbean> topic) {
        this.topic = topic;
        this.topic.attach(this);
    }

    @Override
    public void consume(Testbean dto) {
        log.info("hi1 :dto:{}", dto);
    }
}
