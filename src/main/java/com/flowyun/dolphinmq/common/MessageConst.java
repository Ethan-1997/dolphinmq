package com.flowyun.dolphinmq.common;

import java.util.HashSet;

/**
 * 系统消息常量
 *
 * @author Barry
 * @since 2021/6/28 12:45
 */
public class MessageConst {
    public static final String USER_TOKEN = "TOKEN";

    public static final HashSet<String> STRING_HASH_SET = new HashSet<String>();

    static {
        STRING_HASH_SET.add(USER_TOKEN);
    }
}