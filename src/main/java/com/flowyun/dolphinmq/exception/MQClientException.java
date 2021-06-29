package com.flowyun.dolphinmq.exception;

/**
 * 消息队列客户端异常
 * @author Barry
 * @since 2021/6/28 12:45
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class MQClientException extends Exception {
    private static final long serialVersionUID = -5758410930844185841L;
    private int responseCode;
    private String errorMessage;

    public MQClientException(String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.responseCode = -1;
        this.errorMessage = errorMessage;
    }

    public MQClientException(CodeMsg codeMsg) {
        super("CODE: " + codeMsg.getCode() + "  DESC: "
                + codeMsg.getMsg());
        this.responseCode = codeMsg.getCode();
        this.errorMessage = codeMsg.getMsg();
    }

    public int getResponseCode() {
        return responseCode;
    }

    public MQClientException setResponseCode(final int responseCode) {
        this.responseCode = responseCode;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }
}