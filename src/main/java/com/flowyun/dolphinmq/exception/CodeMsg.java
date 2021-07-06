package com.flowyun.dolphinmq.exception;

/**
 * @author Barry
 */
public class CodeMsg {
    private final int code;
    private final String msg;


    /**
     * 生产端
     */
    public static CodeMsg SUCCESS = new CodeMsg(0, "success");
    public static CodeMsg SERVER_ERROR = new CodeMsg(500100, "服务端异常");
    public static CodeMsg BIND_ERROR = new CodeMsg(500101, "参数校验异常:%s");

    /**
     * 消费端
     */
    public static CodeMsg SESSION_ERROR = new CodeMsg(500210, "Session不存在或为空");


    private CodeMsg(int code, String success) {
        this.code = code;
        this.msg = success;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}