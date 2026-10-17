package com.paytech.ledger.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.api.request.ReviewManualAdjustmentRequestBody;
import com.paytech.ledger.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.ledger.test.checker.LedgerResponseChecker;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.local.LedgerFixture;

/**
 * 人工调整入口测试的公共前置与调用
 * <li>每例准备 AE001/USD 账簿、覆盖今天的 OPEN 期间，以及银行现金（借方正常）、实收资本与 Client 可用应付（贷方正常）三个账户。</li>
 */
public abstract class AbstractManualAdjustmentApiTest extends MockTestBase {
    /** 调整接口根路径。 */
    protected static final String PATH = "/manual-adjustments/v1";

    /** 与模板编制人不同的复核人。 */
    protected static final String CHECKER_ID = "70002";

    @Autowired
    protected AccountingTestMapper testMapper;

    protected LedgerFixture ledger;

    protected long bankCashAccountId;

    protected long capitalAccountId;

    protected long clientAvailableAccountId;

    /** 两级初始化的类级部分：每例重新插入账簿与账户。 */
    @BeforeEach
    void setUpManualAdjustmentMocks() {
        ledger = ledgerMocker.mockOperatingEntityLedger("AE001", "USD");
        bankCashAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), "BANK_CASH", OwnerType.CASH_ACCOUNT,
            "5001", BalanceBucket.NONE);
        capitalAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), "CAPITAL", OwnerType.OPERATING_ENTITY,
            "AE001", BalanceBucket.NONE);
        clientAvailableAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(),
            StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, "1001", BalanceBucket.AVAILABLE);
    }

    /** 本类账簿与开放期间的提交模板。 */
    protected SubmitManualAdjustmentRequestBuilder adjustment() {
        return SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), ledger.getOpenPeriodId());
    }

    /** 提交并断言 201，返回响应。 */
    protected LedgerResponseChecker submitted(SubmitManualAdjustmentRequestBuilder builder) {
        return post(PATH, builder.build()).assertSuccess(201);
    }

    /** 提交一笔调整并由模板复核人批准过账，返回批准响应。 */
    protected LedgerResponseChecker posted(SubmitManualAdjustmentRequestBuilder builder) {
        var submit = submitted(builder);
        return approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash")).assertSuccess(200);
    }

    /** 以指定复核人和摘要批准。 */
    protected LedgerResponseChecker approve(long adjustmentId, String checkerId, String contentHash) {
        return post(PATH + "/" + adjustmentId + "/approve", review(checkerId, contentHash));
    }

    /** 以指定复核人和摘要驳回。 */
    protected LedgerResponseChecker reject(long adjustmentId, String checkerId, String contentHash) {
        return post(PATH + "/" + adjustmentId + "/reject", review(checkerId, contentHash));
    }

    /** 复核请求体。 */
    protected ReviewManualAdjustmentRequestBody review(String checkerId, String contentHash) {
        ReviewManualAdjustmentRequestBody body = new ReviewManualAdjustmentRequestBody();
        body.setCheckerId(checkerId);
        body.setApprovedContentHash(contentHash);
        return body;
    }
}
