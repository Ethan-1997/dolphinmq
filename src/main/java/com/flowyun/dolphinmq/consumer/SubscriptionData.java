package com.flowyun.dolphinmq.consumer;

import com.sun.jdi.ClassType;
import lombok.Data;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 〈一句话功能简述〉
 * 〈功能详细描述〉
 *
 * @author Barry
 * @since 2021/7/6 9:34
 */
@Data
public class SubscriptionData<T> {
    private RStream<Object, Object> stream;
    private String topicName;
    private final List<TopicListener<T>> listeners
            = new ArrayList<>();
    private T dto;
    private Class dtoClazz;

    public SubscriptionData(String topic, RedissonClient client, Class dtoClazz) {
        this.topicName = topic;
        this.dtoClazz = dtoClazz;
        initStream(client);
    }

    private void initStream(RedissonClient client) {
        if (stream == null) {
            stream = client.getStream(topicName);
        }
    }

    public void registerMessageListener(TopicListener<T> listener) {
        listeners.add(listener);
    }

    /**
     * 设置DTO，并且通知所有Listener
     *
     * @param dto dto
     * @author Barry
     * @since 2021/7/5 10:01
     **/
    public void setDto(T dto) {
        this.dto = dto;
        notifyAllListeners(dto);
    }

    public void notifyAllListeners(T dto) {
        for (TopicListener<T> listener : listeners) {
            listener.consume(dto);
        }
    }

}
