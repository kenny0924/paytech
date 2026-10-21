package com.paytech.channel.connector.protocol.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 外部 API 配置静态校验发现的一个问题。 */
@Getter
@AllArgsConstructor
public class ValidationIssue {

    /** 阻止保存的问题。 */
    public static final String ERROR = "ERROR";

    /** 只提示、不阻止保存的问题。 */
    public static final String WARNING = "WARNING";

    /** 严重级别：ERROR 或 WARNING。 */
    private final String severity;

    /** 稳定问题代码，如 FIELD_PATH_INVALID。 */
    private final String code;

    /** 请求体路径，如 requestFields[2].fieldPath。 */
    private final String location;

    /** 可外显的中文说明，不含敏感值。 */
    private final String message;

    /** 阻止保存的问题。 */
    public ValidationIssue(String code, String location, String message) {
        this(ERROR, code, location, message);
    }

    /** 是否阻止保存。 */
    public boolean blocking() {
        return ERROR.equals(severity);
    }
}
