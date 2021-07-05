package com.flowyun.dolphinmq.consumer;

import java.util.ArrayList;
import java.util.List;

/**
 * @param <T> dto类型
 * @author Barry
 */
public class Topic<T> {

    private final List<TopicListener<T>> consumers
            = new ArrayList<>();
    private T dto;

    public T getDto() {
        return dto;
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

    /**
     * 添加监听者
     * @author  Barry
     * @since  2021/7/5 10:03
     * @param listener 监听者
     **/
    public void attach(TopicListener<T> listener) {
        consumers.add(listener);
    }

    public void notifyAllListeners(T dto) {
        for (TopicListener<T> observer : consumers) {
            observer.consume(dto);
        }
    }
}