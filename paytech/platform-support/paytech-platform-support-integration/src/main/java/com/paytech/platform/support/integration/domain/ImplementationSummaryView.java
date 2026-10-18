package com.paytech.platform.support.integration.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道标准 API 实现列表项，摘要从已保存流程提取。 */
@Getter
@AllArgsConstructor
public class ImplementationSummaryView {

    /** 标准 API 编码。 */
    private final String standardApiCode;

    /** 标准 API 名称。 */
    private final String standardApiName;

    /** 节点数。 */
    private final int nodeCount;

    /** 出口数。 */
    private final int returnCount;

    /** 初始化项标识，按声明顺序。 */
    private final List<String> initializerIds;

    /** 流程总预算毫秒数。 */
    private final int timeoutMs;

    /** 当前版本。 */
    private final int version;

    /** 最后更新时间。 */
    private final Instant updateTime;
}
