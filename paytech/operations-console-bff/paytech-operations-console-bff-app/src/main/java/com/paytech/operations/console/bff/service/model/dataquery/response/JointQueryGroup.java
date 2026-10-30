package com.paytech.operations.console.bff.service.model.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 一个键类型分组（链路图泳道）。 */
@Getter
@Setter
public class JointQueryGroup {

    /** 分组显示名。 */
    private String groupName;

    /**
     * 泳道顺序
     * <li>组内 ACTIVE 键类型的最小 sortNo，升序；页面按泳道顺序从固定色板依次取色。</li>
     */
    private int rank;
}
