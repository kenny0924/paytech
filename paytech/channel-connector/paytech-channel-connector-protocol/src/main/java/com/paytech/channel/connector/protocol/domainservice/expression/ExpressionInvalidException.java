package com.paytech.channel.connector.protocol.domainservice.expression;

/** 表达式或脚本不符合白名单剖面，或无法编译；消息为可直接展示给配置人员的中文说明。 */
public class ExpressionInvalidException extends RuntimeException {

    public ExpressionInvalidException(String message) {
        super(message);
    }
}
