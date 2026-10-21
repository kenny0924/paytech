package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道标准 API 实现详情。 */
@Getter
@AllArgsConstructor
public class ImplementationDetailView {

    /** 标准 API 编码。 */
    private final String standardApiCode;

    /** 流程定义。 */
    private final JsonNode flowDefinition;

    /** 当前版本。 */
    private final int version;

    /** 创建时间。 */
    private final Instant createTime;

    /** 最后更新时间。 */
    private final Instant updateTime;

    /** 最后修改的 Operations 账号标识。 */
    private final Long updatedBy;
}
