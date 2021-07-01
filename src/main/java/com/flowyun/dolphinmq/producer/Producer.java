package com.flowyun.dolphinmq.producer;

import com.flowyun.dolphinmq.common.Message;
import com.flowyun.dolphinmq.utils.BeanMapUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RFuture;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.TrimStrategy;

/**
 * 生产者基类
 *
 * @author Barry
 * @since 2021/6/28 14:40
 */
@Slf4j
public class Producer {
    private RedissonClient client;
    private RStream<Object, Object> stream;
    /**
     * 超过了该长度stream前面部分会被持久化（非严格模式——MAXLEN~）
     */
    private Integer trimThreashold;

    public Producer(RedissonClient client) {
        this.client = client;
        this.trimThreashold = 1000;
    }

    /**
     * 异步发送消息到Redis
     *
     * @param msg 消息
     * @return RFuture
     * @author Barry
     * @since 2021/6/28 15:38
     **/
    public void sendMessageAsync(Message msg) {
        stream = client.getStream(msg.getTopic());
        RFuture<Void> sendMessageFuture =
                stream.addAsync(
                        msg.getId(),
                        StreamAddArgs.entries(BeanMapUtils.getObjectObjectMap(msg.getProperties())).trim(TrimStrategy.MAXLEN, trimThreashold));
        sendMessageFuture.thenAccept(res -> {
            log.debug("stream : {} add message:{} success",
                    msg.getTopic(),
                    msg.getProperties());
        }).exceptionally(exception -> {
            log.debug("stream : {} add message:{} error, exception:{}",
                    msg.getTopic(),
                    msg.getProperties(),
                    exception.getMessage());
            return null;
        });
    }
}
