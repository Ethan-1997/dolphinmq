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
    private Map<String, String> properties;

    public Message() {
    }

    public Message(String topic) {
        this.topic = topic;
    }

    void putProperty(final String name, final String value) {
        if (null == this.properties) {
            this.properties = new HashMap<String, String>();
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

    public String getUserProperty(final String name) {
        return this.getProperty(name);
    }

    public String getProperty(final String name) {
        if (null == this.properties) {
            this.properties = new HashMap<String, String>();
        }

        return this.properties.get(name);
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    void setProperties(Map<String, String> properties) {
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