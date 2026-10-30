package com.paytech.tax.domain.document;

import java.util.Map;

import com.paytech.tax.core.dal.dataobject.TaxDocument;
import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.core.dal.dataobject.TaxTransaction;
import com.paytech.tax.domain.calculation.ResolvedTaxPolicy;
import com.paytech.tax.domain.calculation.TaxCalculationView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 开具一张简化税票所需的已登记事实：交易、冻结计算、开票注册与地区包、税类名称，以及已占号的单据（若有）。 */
@Getter
@AllArgsConstructor
public class TaxDocumentIssueContext {
    /** 正式交易 */
    private final TaxTransaction transaction;

    /** 冻结计算 */
    private final TaxCalculationView calculation;

    /** 票面税号所属注册（首个应税组件的注册） */
    private final TaxRegistration registration;

    /** 该组件的地区包（documents.receipt） */
    private final ResolvedTaxPolicy policy;

    /** 税类代码 → 名称（行描述） */
    private final Map<String, String> categoryNames;

    /** 已占号的单据；尚未分号为空 */
    private final TaxDocument document;
}
