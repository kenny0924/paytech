package com.paytech.platform.support.integration.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 标准 API 详情，组合主表、字段与实现方。 */
@Getter
@AllArgsConstructor
public class StandardApiDetailView {

    /** 标准 API 编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 用途说明，可为 null。 */
    private final String description;

    /** 请求字段，按显示顺序。 */
    private final List<FieldDefinition> requestFields;

    /** 响应字段，按显示顺序。 */
    private final List<FieldDefinition> responseFields;

    /** 已实现的接入方，按编码排序。 */
    private final List<ConfigurationReference> implementations;

    /** 当前版本。 */
    private final int version;

    /** 创建时间。 */
    private final Instant createTime;

    /** 最后修改时间。 */
    private final Instant updateTime;

    /** 最后修改的 Operations 账号。 */
    private final long updatedBy;
}
