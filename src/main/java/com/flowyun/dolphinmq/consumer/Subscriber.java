package com.flowyun.dolphinmq.consumer;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 〈一句话功能简述〉
 * 〈功能详细描述〉
 *
 * @author Barry
 * @since 2021/7/6 9:34
 */
@Getter
@Setter
public class Subscriber<T> {
    private RStream<Object, Object> stream;
    private String topicName;
    private final List<MsgListener<T>> listeners = new ArrayList<>();
    private PullConsumerClient pullConsumerClient;
    private RedissonClient redissonClient;

    public Subscriber(String topic, RedissonClient redissonClient, PullConsumerClient pullConsumerClient) {
        this.topicName = topic;
        initStream(redissonClient);
        this.redissonClient = redissonClient;
        this.pullConsumerClient = pullConsumerClient;
    }

    private Class<?> getSuperClassGenericType(final Class<?> clazz, final int index) {
        Type genType = clazz.getGenericSuperclass();
        if (!(genType instanceof ParameterizedType)) {
            return Object.class;
        }
        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
        if (index >= params.length || index < 0) {
            return Object.class;
        }
        if (!(params[index] instanceof Class)) {
            return Object.class;
        }
        return (Class<?>) params[index];
    }

    public Class<T> getMsgClass() {
        return (Class<T>) getSuperClassGenericType(getClass(), 0);
    }

    private void initStream(RedissonClient client) {
        if (stream == null) {
            stream = client.getStream(topicName);
        }
    }

    public Subscriber<T> registerListener(MsgListener<T> listener) {
        listeners.add(listener);
        return this;
    }

    public void notify(T dto) {
        for (MsgListener<T> listener : listeners) {
            listener.consume(dto);
        }
    }

    /**
     * 订阅主题
     *
     * @param topic 主题名
     * @return 返回SubscriptionData
     * @author Barry
     * @since 2021/7/6 9:56
     */
    public <T> Subscriber<T> subscribe(String topic) {
        return pullConsumerClient.subscribe(topic);
    }

    public void start() {
        pullConsumerClient.start();
    }

}
