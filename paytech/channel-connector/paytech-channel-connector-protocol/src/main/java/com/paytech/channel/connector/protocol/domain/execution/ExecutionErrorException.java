package com.paytech.channel.connector.protocol.domain.execution;

/**
 * 第一个节点开始后的执行失败：节点内记为该节点 ERROR，出边或出口中则整次调用 ERROR。
 *
 * <p>
 * 消息只用固定文字和配置位置，不带字段值、报文或凭据。
 */
public class ExecutionErrorException extends RuntimeException {

    public ExecutionErrorException(String message) {
        super(message);
    }

    public ExecutionErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
