package com.paytech.commercial.domain;

import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 标准价目模板方案列表中的一行：模板方案、方案 BCR 根版本、当前生效版本与最新版本（多表组合查询结果）。 */
@Getter
@AllArgsConstructor
public class CommercialTermTemplateSummaryView {
    /** 模板方案 */
    private final CommercialTermPlan plan;

    /** 方案 BCR 根当前版本 */
    private final long changeVersion;

    /** 当前 ACTIVE 版本，没有为空 */
    private final CommercialTermVersion activeVersion;

    /** 版本号最大的版本，没有为空 */
    private final CommercialTermVersion latestVersion;
}
