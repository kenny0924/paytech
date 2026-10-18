package com.paytech.platform.support.integration.domain;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 原子 API 详情，组合主表、字段与流程引用。 */
@Getter
@AllArgsConstructor
public class IntegrationApiDetailView {

    /** 原子 API 编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 说明，可为 null。 */
    private final String description;

    /** 请求字段。 */
    private final List<FieldDefinition> requestFields;

    /** 响应字段。 */
    private final List<FieldDefinition> responseFields;

    /** 执行定义。 */
    private final JsonNode definition;

    /** 引用该 API 的实现，按标准 API 编码排序。 */
    private final List<ApiReference> referencedBy;

    /** 当前版本。 */
    private final int version;

    /** 创建时间。 */
    private final Instant createTime;

    /** 最后修改时间。 */
    private final Instant updateTime;

    /** 最后修改的 Operations 账号。 */
    private final long updatedBy;
}
