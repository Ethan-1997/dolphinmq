package com.flowyun.dolphinmq.common;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.redisson.api.StreamMessageId;

import java.io.Serializable;
import java.util.Map;

/**
 * 消息
 *
 * @author Barry
 * @since 2021/6/28 14:40
 */
@Getter
@Setter
public class Message implements Serializable {
    private static final long serialVersionUID = 8445773977080406428L;

    private StreamMessageId id;
    private String topic;
    private Map<String, Object> properties;

    {
        //id自动生成
        SequenceUtil sq = SequenceUtil.getInstance();
        long nextId = sq.nextId();
        this.id = new StreamMessageId(nextId / 4194304, nextId % 4194304);
    }

    public Message() {
    }

    public Message(String topic) {
        this.topic = topic;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                "topic='" + topic + '\'' +
                ", properties=" + properties +
                '}';
    }
}