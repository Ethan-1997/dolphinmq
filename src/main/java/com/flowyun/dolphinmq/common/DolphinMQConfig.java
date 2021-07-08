package com.flowyun.dolphinmq.common;

import lombok.Data;

/**
 * 配置文件
 *
 * @author Barry
 * @since 2021/7/8 15=49
 */
@SuppressWarnings("ALL")
@Data
public class DolphinMQConfig {
    /**
     * 每次拉取数据的量
     */
    private Integer fetchMessageSize;
    /**
     * 检查consumer不活跃的门槛（单位秒）
     */
    private Integer pendingListIdleThreshold;
    /**
     * 每次拉取PendingList的大小
     */
    private Integer checkPendingListSize;
    /**
     * 死信门槛
     */
    private Integer deadLetterThreshold;
    /**
     * 认领门槛
     */
    private Integer claimThreshold;
    /**
     * 是否从头开始订阅消息
     */
    private Boolean isStartFromHead ;
    /**
     * 拉取信息的周期
     */
    private Integer pullHealthyMessagesPeriod;
    /**
     * 检查PendingList周期
     */
    private Integer checkPendingListsPeriod;

    DolphinMQConfig() {
    }

    {
        fetchMessageSize = 5;
        pendingListIdleThreshold = 10;
        checkPendingListSize = 1000;
        deadLetterThreshold = 32;
        claimThreshold = 3600;
        isStartFromHead = true;
        pullHealthyMessagesPeriod = 1;
        checkPendingListsPeriod = 5;
    }
}
