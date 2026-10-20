package com.paytech.ledger.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.request.ReviewManualAdjustmentRequestBody;
import com.paytech.ledger.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.ledger.test.checker.LedgerResponseChecker;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.local.LedgerFixture;

/**
 * 人工调整入口测试的公共前置与调用
 * <li>每例准备 AE001/USD 账簿、覆盖今天的 OPEN 期间，以及银行现金（借方正常）、实收资本与待识别资金（贷方正常）三个科目账户。</li>
 * <li>第 11b 步起人工调整不得涉及 Client / Local Partner 账户（design/04 §4）：原 Client 可用应付账户改为经营主体的待识别资金负债账户，
 * Client 账户只在验证该禁令的用例中创建。</li>
 */
public abstract class AbstractManualAdjustmentApiTest extends MockTestBase {
    /** 调整接口根路径。 */
    protected static final String PATH = "/manual-adjustments/v1";

    /** 贷方正常的经营主体负债科目：待识别资金。 */
    protected static final String LIABILITY_CODE = "UNIDENTIFIED_CASH";

    /** 测试使用的权益科目（STANDARD 30010101）。 */
    protected static final String CAPITAL_CODE = "CAPITAL";

    /** 与模板编制人不同的复核人。 */
    protected static final String CHECKER_ID = "70002";

    @Autowired
    protected AccountingTestMapper testMapper;

    protected LedgerFixture ledger;

    protected long bankCashAccountId;

    protected long capitalAccountId;

    protected long liabilityAccountId;

    /** 两级初始化的类级部分：每例重新插入账簿与账户。 */
    @BeforeEach
    void setUpManualAdjustmentMocks() {
        ledger = ledgerMocker.mockOperatingEntityLedger("AE001", "USD");
        bankCashAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), "BANK_CASH", LedgerOwnerType.CASH_ACCOUNT,
            "5001", BalanceBucket.NONE);
        capitalAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), CAPITAL_CODE, LedgerOwnerType.OPERATING_ENTITY,
            "AE001", BalanceBucket.NONE);
        liabilityAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), LIABILITY_CODE,
            LedgerOwnerType.OPERATING_ENTITY, "AE001", BalanceBucket.NONE);
    }

    /** 本类账簿与开放期间的提交模板。 */
    protected SubmitManualAdjustmentRequestBuilder adjustment() {
        return SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), ledger.getOpenPeriodId());
    }

    /** 以模板编制人提交，返回原始响应。 */
    protected LedgerResponseChecker submit(SubmitManualAdjustmentRequestBuilder builder) {
        return submit(builder, SubmitManualAdjustmentRequestBuilder.MAKER_ID);
    }

    /** 以指定编制人（操作人 Header）提交，返回原始响应。 */
    protected LedgerResponseChecker submit(SubmitManualAdjustmentRequestBuilder builder, String makerId) {
        return postAs(PATH, makerId, builder.build());
    }

    /** 以模板编制人提交并断言 201，返回响应。 */
    protected LedgerResponseChecker submitted(SubmitManualAdjustmentRequestBuilder builder) {
        return submit(builder).assertSuccess(201);
    }

    /** 提交一笔调整并由模板复核人批准过账，返回批准响应。 */
    protected LedgerResponseChecker posted(SubmitManualAdjustmentRequestBuilder builder) {
        var submit = submitted(builder);
        return approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash")).assertSuccess(200);
    }

    /** 以指定复核人（操作人 Header）和摘要批准。 */
    protected LedgerResponseChecker approve(long adjustmentId, String checkerId, String contentHash) {
        return postAs(PATH + "/" + adjustmentId + "/approve", checkerId, review(contentHash));
    }

    /** 以指定复核人（操作人 Header）和摘要驳回。 */
    protected LedgerResponseChecker reject(long adjustmentId, String checkerId, String contentHash) {
        return postAs(PATH + "/" + adjustmentId + "/reject", checkerId, review(contentHash));
    }

    /** 复核请求体。 */
    protected ReviewManualAdjustmentRequestBody review(String contentHash) {
        ReviewManualAdjustmentRequestBody body = new ReviewManualAdjustmentRequestBody();
        body.setApprovedContentHash(contentHash);
        return body;
    }
}
