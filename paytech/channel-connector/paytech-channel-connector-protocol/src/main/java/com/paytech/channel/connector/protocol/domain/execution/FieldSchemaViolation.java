package com.paytech.channel.connector.protocol.domain.execution;

import lombok.Getter;

/** 值不满足字段结构：位置为点分路径（数组元素带下标），消息为固定说明，不带字段值。 */
@Getter
public class FieldSchemaViolation extends RuntimeException {

    /** 不满足结构的位置，根为空串。 */
    private final String path;

    public FieldSchemaViolation(String path, String message) {
        super(message);
        this.path = path;
    }
}
