package com.paytech.channel.connector.protocol.domain;

import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 外部 API 配置一次变更中一个受控字段的前后摘要；取值已是名称、编码或 SHA-256 摘要，不含原文。 */
@Getter
@AllArgsConstructor
public class ConfigurationFieldChange {

    /** 受控字段名。 */
    private final ConfigurationField field;

    /** 变更前的摘要，创建时为 null。 */
    private final String before;

    /** 变更后的摘要，删除时为 null。 */
    private final String after;
}
