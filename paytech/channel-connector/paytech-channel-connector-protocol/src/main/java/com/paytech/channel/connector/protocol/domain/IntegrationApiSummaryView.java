package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 原子 API 列表项，组合原子 API、定义摘要与流程引用。 */
@Getter
@AllArgsConstructor
public class IntegrationApiSummaryView {

    /** 原子 API 编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** HTTP 方法。 */
    private final String method;

    /** 出站地址代码。 */
    private final String endpointCode;

    /** 路径模板。 */
    private final String pathTemplate;

    /** 请求阶段脚本数。 */
    private final int requestScriptCount;

    /** 响应阶段脚本数。 */
    private final int responseScriptCount;

    /** 引用该 API 的标准 API 编码，按编码排序。 */
    private final List<String> referencedBy;

    /** 当前版本。 */
    private final int version;

    /** 最后修改时间。 */
    private final Instant updateTime;
}
