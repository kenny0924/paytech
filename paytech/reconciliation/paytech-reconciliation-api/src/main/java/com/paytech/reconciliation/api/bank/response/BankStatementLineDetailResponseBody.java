package com.paytech.reconciliation.api.bank.response;

import java.util.List;

import com.paytech.reconciliation.api.exception.response.ReconExceptionResponseBody;

import lombok.Getter;
import lombok.Setter;

/** 银行流水详情：流水、所属对账单、全部银行匹配（含对应预期银行流水）与流水上的未结异常。 */
@Getter
@Setter
public class BankStatementLineDetailResponseBody {
    /** 银行流水 */
    private BankStatementLineResponseBody line;

    /** 所属银行对账单 */
    private BankStatementResponseBody statement;

    /**
     * 本流水的全部银行匹配
     * <li>含 PROPOSED、CONFIRMED、REJECTED，按匹配主键升序；没有时为空列表。</li>
     */
    private List<Match> matches;

    /**
     * 挂在本流水上的未结异常（OPEN、UNDER_REVIEW）
     * <li>按异常主键升序；没有时为空列表。</li>
     */
    private List<ReconExceptionResponseBody> openExceptions;

    /** 一条银行匹配与它分配到的预期银行流水。 */
    @Getter
    @Setter
    public static class Match {
        /** 银行匹配 */
        private BankMatchResponseBody match;

        /** 匹配的预期银行流水（来源类型、来源 ID、预期与已确认金额、状态） */
        private ExpectedBankMovementResponseBody expectedMovement;
    }
}
