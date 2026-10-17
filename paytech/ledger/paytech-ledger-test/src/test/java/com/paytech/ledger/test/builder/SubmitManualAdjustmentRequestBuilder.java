package com.paytech.ledger.test.builder;

import java.util.ArrayList;
import java.util.List;

import com.paytech.ledger.api.request.SubmitManualAdjustmentRequestBody;
import com.paytech.ledger.core.enums.EntrySide;

/** 人工调整提交请求构建器：模板提供合法头字段，明细由用例显式添加；不修复负例。 */
public final class SubmitManualAdjustmentRequestBuilder {
    /** 模板编制人。 */
    public static final String MAKER_ID = "70001";

    private final SubmitManualAdjustmentRequestBody body = new SubmitManualAdjustmentRequestBody();

    private SubmitManualAdjustmentRequestBuilder() {
        body.setLines(new ArrayList<>());
    }

    /** USD、CORRECTION 原因、模板编制人、无附件、无明细。 */
    public static SubmitManualAdjustmentRequestBuilder template(long ledgerBookId, long accountingPeriodId) {
        SubmitManualAdjustmentRequestBuilder builder = new SubmitManualAdjustmentRequestBuilder();
        builder.body.setLedgerBookId(String.valueOf(ledgerBookId));
        builder.body.setAccountingPeriodId(String.valueOf(accountingPeriodId));
        builder.body.setCurrency("USD");
        builder.body.setReasonCode("CORRECTION");
        builder.body.setMakerId(MAKER_ID);
        return builder;
    }

    /** 追加一条明细。 */
    public SubmitManualAdjustmentRequestBuilder line(long accountId, EntrySide side, String amount) {
        SubmitManualAdjustmentRequestBody.Line line = new SubmitManualAdjustmentRequestBody.Line();
        line.setAccountId(String.valueOf(accountId));
        line.setEntrySide(side.getCode());
        line.setAmount(amount);
        line.setLineDescription("测试调整 " + side.getCode() + " " + amount);
        body.getLines().add(line);
        return this;
    }

    /** 借记一个账户、贷记另一个账户相同金额。 */
    public SubmitManualAdjustmentRequestBuilder transfer(long debitAccountId, long creditAccountId, String amount) {
        return line(debitAccountId, EntrySide.DEBIT, amount).line(creditAccountId, EntrySide.CREDIT, amount);
    }

    /** 覆盖币种。 */
    public SubmitManualAdjustmentRequestBuilder currency(String currency) {
        body.setCurrency(currency);
        return this;
    }

    /** 覆盖证据文件。 */
    public SubmitManualAdjustmentRequestBuilder file(String fileId, String fileContentHash) {
        body.setFileId(fileId);
        body.setFileContentHash(fileContentHash);
        return this;
    }

    /** 覆盖编制人。 */
    public SubmitManualAdjustmentRequestBuilder maker(String makerId) {
        body.setMakerId(makerId);
        return this;
    }

    /** 返回当前请求体；不补默认值。 */
    public SubmitManualAdjustmentRequestBody build() {
        return body;
    }

    /** 当前明细，供用例构造负例。 */
    public List<SubmitManualAdjustmentRequestBody.Line> lines() {
        return body.getLines();
    }
}
