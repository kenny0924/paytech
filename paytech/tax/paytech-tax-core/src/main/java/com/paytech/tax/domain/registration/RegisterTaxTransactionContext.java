package com.paytech.tax.domain.registration;

import java.util.List;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.tax.core.dal.dataobject.TaxReportingEntry;
import com.paytech.tax.core.dal.dataobject.TaxTransaction;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次正式登记待原子保存的交易、底稿与（需要开票时的）开票补偿任务。 */
@Getter
@AllArgsConstructor
public class RegisterTaxTransactionContext {
    /** 正式交易 */
    private final TaxTransaction transaction;

    /** OUTPUT 底稿；缺法定汇率的组件不建 */
    private final List<TaxReportingEntry> entries;

    /** TAX_DOCUMENT_ISSUE 登记请求；地区包不要求开票时为空 */
    private final CompensationEventRequest documentIssueEvent;
}
