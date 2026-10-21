package com.paytech.reconciliation.service.processor.response;

import java.util.List;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.domain.LineRejection;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 银行对账单完成导入结果：IMPORTED 带连续性异常；REJECTED 带逐行原因（只在首次完成时非空）。 */
@Getter
@AllArgsConstructor
public class CompleteBankStatementResponse {
    /** 完成后的对账单 */
    private final BankStatement statement;

    /** 逐行拒绝原因；整单原因的行号为空 */
    private final List<LineRejection> rejections;

    /** 与上一张接不上时开立的 BANK_STATEMENT_GAP 异常主键；没有为空 */
    private final Long gapExceptionId;
}
