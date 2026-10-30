package com.paytech.platform.support.dataquery.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 链路图泳道：同名分组的键类型，顺序取组内 ACTIVE 键类型的最小 sort_no。 */
@Getter
@AllArgsConstructor
public final class JointQueryGroup {

    /** 分组显示名。 */
    private final String groupName;

    /** 泳道顺序。 */
    private final int rank;
}
