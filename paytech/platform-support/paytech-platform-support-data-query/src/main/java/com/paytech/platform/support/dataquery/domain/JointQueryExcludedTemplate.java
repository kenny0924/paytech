package com.paytech.platform.support.dataquery.domain;

import com.paytech.platform.support.dataquery.core.enums.JointQueryExclusionReason;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 配置了联合查询却不参与的模板及原因。 */
@Getter
@AllArgsConstructor
public final class JointQueryExcludedTemplate {

    /** 模板编码。 */
    private final String templateCode;

    /** 原因。 */
    private final JointQueryExclusionReason reason;

    /** 中文说明。 */
    private final String message;
}
