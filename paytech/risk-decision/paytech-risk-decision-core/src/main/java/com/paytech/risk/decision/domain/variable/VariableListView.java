package com.paytech.risk.decision.domain.variable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 当前根、成员预览与真实分页方式的查询投影。 */
@Getter
@AllArgsConstructor
public class VariableListView {
    /** 当前事实及缺失编码。 */
    private final RiskVariable.Page page;
    /** 普通查询页码；批取为空。 */
    private final Integer pageNumber;
    /** 普通查询每页数量；批取为空。 */
    private final Integer pageSize;
}
