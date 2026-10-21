package com.paytech.channel.connector.protocol.domain.execution;

import java.util.List;

import com.paytech.channel.connector.protocol.core.enums.StandardApiNotSentCode;

import lombok.Getter;

/**
 * 标准 API 执行在第一个原子节点开始前失败：保证没有发出任何渠道请求
 * <li>进程内调用方据此把结果映射为未发出（NOT_SENT）；其他异常一律视为结果未知。</li>
 * <li>消息只含编码与配置定位，不含请求输入、凭据或渠道报文。</li>
 */
@Getter
public class StandardApiNotSentException extends RuntimeException {

    /** 未发出原因。 */
    private final StandardApiNotSentCode reason;

    /** 输入校验失败的字段位置（以 input 开头）；其他原因为空列表。 */
    private final List<String> fields;

    /**
     * 未发出失败
     *
     * @param reason 未发出原因
     * @param message 安全的中文说明
     */
    public StandardApiNotSentException(StandardApiNotSentCode reason, String message) {
        this(reason, message, List.of());
    }

    /**
     * 带字段位置的未发出失败
     *
     * @param reason 未发出原因
     * @param message 安全的中文说明
     * @param fields 出错的字段位置
     */
    public StandardApiNotSentException(StandardApiNotSentCode reason, String message, List<String> fields) {
        super(message);
        this.reason = reason;
        this.fields = List.copyOf(fields);
    }

    /** 稳定代码，与原 Platform Problem code 一致。 */
    public String getCode() {
        return reason.getCode();
    }
}
