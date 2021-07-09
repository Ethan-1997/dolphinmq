package com.flowyun.dolphinmq.common;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * 配置文件
 *
 * @author Barry
 * @since 2021/7/8 15=49
 */
@SuppressWarnings("ALL")
@Getter
@Setter
@Configuration
public class DolphinMQConfig {
    /**
     * 每次拉取数据的量
     */
    @Value("${af.dolphinmq.fetchMessageSize:5}")
    private Integer fetchMessageSize;
    /**
     * 检查consumer不活跃的门槛（单位秒）
     */
    @Value("${af.dolphinmq.pendingListIdleThreshold:10}")
    private Integer pendingListIdleThreshold;
    /**
     * 每次拉取PendingList的大小
     */
    @Value("${af.dolphinmq.checkPendingListSize:1000}")
    private Integer checkPendingListSize;
    /**
     * 死信门槛
     */
    @Value("${af.dolphinmq.deadLetterThreshold:32}")
    private Integer deadLetterThreshold;
    /**
     * 认领门槛
     */
    @Value("${af.dolphinmq.claimThreshold:3600}")
    private Integer claimThreshold;
    /**
     * 是否从头开始订阅消息
     */
    @Value("${af.dolphinmq.isStartFromHead:true}")
    private Boolean isStartFromHead;
    /**
     * 拉取信息的周期
     */
    @Value("${af.dolphinmq.pullHealthyMessagesPeriod:1}")
    private Integer pullHealthyMessagesPeriod;
    /**
     * 检查PendingList周期
     */
    @Value("${af.dolphinmq.checkPendingListsPeriod:10}")
    private Integer checkPendingListsPeriod;

}
