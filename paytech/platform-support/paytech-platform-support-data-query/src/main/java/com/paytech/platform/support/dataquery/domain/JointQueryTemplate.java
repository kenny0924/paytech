package com.paytech.platform.support.dataquery.domain;

import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;

import lombok.Builder;
import lombok.Getter;

/** 参与联合查询的模板（ACTIVE 模板当前版本）：触发方式、泳道位置与列标注。 */
@Getter
@Builder
public final class JointQueryTemplate {

    /** 模板编码。 */
    private final String templateCode;

    /** 模板名称。 */
    private final String templateName;

    /** 当前版本号。 */
    private final int versionNo;

    /** 数据源编码。 */
    private final String dataSourceCode;

    /** 行主体键类型所在分组。 */
    private final String groupName;

    /** 泳道顺序。 */
    private final int laneRank;

    /** 行主体键类型的 sort_no，泳道内顺序。 */
    private final int subjectSortNo;

    /** 键参数是否为 LIST。 */
    private final boolean keyParameterList;

    /** 分页方式。 */
    private final QueryPagingMode pagingMode;

    /** 联合查询配置。 */
    private final QueryJointConfig joint;

    /**
     * 触发键类型。
     *
     * @return 键参数的键类型编码
     */
    public String triggerKeyType() {
        return joint.getKeyParameter().getKeyType();
    }
}
