package com.paytech.finance.ext.rest.accounting.converter;

import org.springframework.stereotype.Component;

import com.paytech.finance.accounting.domain.view.TrialBalanceDetailView;
import com.paytech.finance.accounting.domain.view.TrialBalanceFindingView;
import com.paytech.finance.accounting.domain.view.TrialBalanceLineView;
import com.paytech.finance.accounting.domain.view.TrialBalanceRunView;
import com.paytech.finance.api.accounting.response.TrialBalanceDetailResponseBody;
import com.paytech.finance.api.accounting.response.TrialBalanceRunResponseBody;

/** 试算 View 到 ResponseBody 的转换；ID、序号与金额输出为字符串。 */
@Component
public class TrialBalanceConverter {

    /** 转换试算摘要。 */
    public TrialBalanceRunResponseBody toResponseBody(TrialBalanceRunView view) {
        TrialBalanceRunResponseBody body = new TrialBalanceRunResponseBody();
        body.setTrialBalanceRunId(view.getTrialBalanceRunId().toString());
        body.setLedgerBookId(view.getLedgerBookId().toString());
        body.setPeriodId(view.getPeriodId().toString());
        body.setRunNo(view.getRunNo().toString());
        body.setAsOfSequence(view.getAsOfSequence().toString());
        body.setStatus(view.getStatus().getCode());
        body.setTotalDebit(view.getTotalDebit().toPlainString());
        body.setTotalCredit(view.getTotalCredit().toPlainString());
        body.setContentHash(view.getContentHash());
        body.setLineCount(view.getLineCount());
        body.setExceptionCount(view.getExceptionCount());
        body.setCreateTime(view.getCreateTime());
        return body;
    }

    /** 转换试算详情。 */
    public TrialBalanceDetailResponseBody toResponseBody(TrialBalanceDetailView view) {
        TrialBalanceRunView run = view.getRun();
        TrialBalanceDetailResponseBody body = new TrialBalanceDetailResponseBody();
        body.setTrialBalanceRunId(run.getTrialBalanceRunId().toString());
        body.setLedgerBookId(run.getLedgerBookId().toString());
        body.setPeriodId(run.getPeriodId().toString());
        body.setRunNo(run.getRunNo().toString());
        body.setAsOfSequence(run.getAsOfSequence().toString());
        body.setStatus(run.getStatus().getCode());
        body.setTotalDebit(run.getTotalDebit().toPlainString());
        body.setTotalCredit(run.getTotalCredit().toPlainString());
        body.setContentHash(run.getContentHash());
        body.setLineCount(run.getLineCount());
        body.setExceptionCount(run.getExceptionCount());
        body.setCreateTime(run.getCreateTime());
        body.setLines(view.getLines().stream().map(this::toLineBody).toList());
        body.setExceptions(view.getFindings().stream().map(this::toFindingBody).toList());
        return body;
    }

    /** 转换试算行。 */
    TrialBalanceDetailResponseBody.Line toLineBody(TrialBalanceLineView line) {
        TrialBalanceDetailResponseBody.Line body = new TrialBalanceDetailResponseBody.Line();
        body.setChartAccountId(line.getChartAccountId().toString());
        body.setAccountCode(line.getAccountCode());
        body.setAccountName(line.getAccountName());
        body.setAccountClass(line.getAccountClass().getCode());
        body.setNormalSide(line.getNormalSide().getCode());
        body.setOpeningBalance(line.getOpeningBalance().toPlainString());
        body.setPeriodDebit(line.getPeriodDebit().toPlainString());
        body.setPeriodCredit(line.getPeriodCredit().toPlainString());
        body.setClosingBalance(line.getClosingBalance().toPlainString());
        return body;
    }

    /** 转换异常发现事实。 */
    TrialBalanceDetailResponseBody.Finding toFindingBody(TrialBalanceFindingView finding) {
        TrialBalanceDetailResponseBody.Finding body = new TrialBalanceDetailResponseBody.Finding();
        body.setExceptionType(finding.getExceptionType().getCode());
        body.setReferenceType(finding.getReferenceType().getCode());
        body.setReferenceId(finding.getReferenceId().toString());
        body.setDifferenceAmount(finding.getDifferenceAmount().toPlainString());
        body.setStatus(finding.getStatus().getCode());
        return body;
    }
}
