package com.paytech.channel.connector.protocol.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 关联配置的编码与名称，用于列表与详情展示引用关系。 */
@Getter
@AllArgsConstructor
public class ConfigurationReference {

    /** 被引用或引用方的编码。 */
    private final String code;

    /** 名称。 */
    private final String name;
}
