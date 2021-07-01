package com.flowyun.dolphinmq.common;

import org.redisson.api.StreamMessageId;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息
 *
 * @author Barry
 * @since 2021/6/28 14:40
 */
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

    void putProperty(final String name, final String value) {
        if (null == this.properties) {
            this.properties = new HashMap<String, Object>();
        }

        this.properties.put(name, value);
    }

    void clearProperty(final String name) {
        if (null != this.properties) {
            this.properties.remove(name);
        }
    }

    public void putUserProperty(final String name, final String value) {
        if (MessageConst.STRING_HASH_SET.contains(name)) {
            throw new RuntimeException(String.format(
                    "The Property<%s> is used by system, input another please", name));
        }

        if (value == null || value.trim().isEmpty()
                || name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "The name or value of property can not be null or blank string!"
            );
        }

        this.putProperty(name, value);
    }

    public Object getUserProperty(final String name) {
        return this.getProperty(name);
    }

    public Object getProperty(final String name) {
        if (null == this.properties) {
            this.properties = new HashMap<String, Object>();
        }

        return this.properties.get(name);
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public StreamMessageId getId() {
        return id;
    }

    public void setId(StreamMessageId id) {
        this.id = id;
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