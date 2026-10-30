package com.paytech.platform.support.api.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 可达预览中的一个泳道及其模板。 */
@Getter
@Setter
public class JointQueryGraphGroup {

    /** 分组显示名。 */
    private String groupName;

    /** 泳道顺序：组内 ACTIVE 键类型的最小 sortNo。 */
    private int rank;

    /** 行主体属于该分组的可达模板，按主体键类型 sortNo、模板编码升序。 */
    private List<JointQueryGraphTemplate> templates;
}
