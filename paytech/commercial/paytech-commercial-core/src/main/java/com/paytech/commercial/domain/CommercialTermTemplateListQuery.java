package com.paytech.commercial.domain;

import com.paytech.commercial.api.enums.CommercialTermPlanStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 标准价目模板方案列表的查询条件：全部可空，空表示不筛选。 */
@Getter
@AllArgsConstructor
public class CommercialTermTemplateListQuery {
    /** 模板 OE */
    private final String entityNo;

    /** 条款类型 */
    private final String termType;

    /** 方案状态 */
    private final CommercialTermPlanStatus status;
}
