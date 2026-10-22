package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 标准 API 列表项，组合标准 API、字段数量与实现方三类数据。 */
@Getter
@AllArgsConstructor
public class StandardApiSummaryView {

    /** 标准 API 编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 请求字段数。 */
    private final int requestFieldCount;

    /** 响应字段数。 */
    private final int responseFieldCount;

    /** 已实现的渠道，按编码排序。 */
    private final List<ConfigurationReference> implementedBy;

    /** 当前版本。 */
    private final int version;

    /** 最后修改时间。 */
    private final Instant updateTime;
}
