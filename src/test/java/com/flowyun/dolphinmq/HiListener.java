package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.MsgListener;
import lombok.extern.slf4j.Slf4j;

/**
 * HiListener
 *
 * @author Barry
 * @since 2021/7/5 10:07
 */
@Slf4j
public class HiListener<T> extends MsgListener<T> {

    @Override
    public void consume(T dto) {
        log.info("hi1 :dto:{}", dto);
    }
}
