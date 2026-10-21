package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 接入方详情，组合接入方、地址引用与下级对象数量。 */
@Getter
@AllArgsConstructor
public class IntegrationDetailView {

    /** 接入方编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 说明，可为 null。 */
    private final String description;

    /** 出站地址，保持保存顺序。 */
    private final List<EndpointUsage> endpoints;

    /** 原子 API 数。 */
    private final long apiCount;

    /** 公共脚本数。 */
    private final long scriptCount;

    /** 标准 API 实现数。 */
    private final long implementationCount;

    /** 当前版本。 */
    private final int version;

    /** 创建时间。 */
    private final Instant createTime;

    /** 最后修改时间。 */
    private final Instant updateTime;

    /** 最后修改的 Operations 账号。 */
    private final long updatedBy;
}
