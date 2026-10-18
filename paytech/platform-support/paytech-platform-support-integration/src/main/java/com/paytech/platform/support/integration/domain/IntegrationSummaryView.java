package com.paytech.platform.support.integration.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 接入方列表项，组合接入方、下级对象数量与已实现的标准 API。 */
@Getter
@AllArgsConstructor
public class IntegrationSummaryView {

    /** 接入方编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 出站地址数。 */
    private final int endpointCount;

    /** 原子 API 数。 */
    private final long apiCount;

    /** 公共脚本数。 */
    private final long scriptCount;

    /** 已实现的标准 API，按编码排序。 */
    private final List<ConfigurationReference> implementedStandardApis;

    /** 当前版本。 */
    private final int version;

    /** 最后修改时间。 */
    private final Instant updateTime;
}
