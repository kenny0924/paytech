package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个键类型及其值：联合查询一步的输入，或一步发现的链路键。 */
@Getter
@AllArgsConstructor
public final class JointQueryKey {

    /** 键类型编码。 */
    private final String keyType;

    /** 值；入口已规范化并去重，保持输入顺序。 */
    private final List<String> values;
}
