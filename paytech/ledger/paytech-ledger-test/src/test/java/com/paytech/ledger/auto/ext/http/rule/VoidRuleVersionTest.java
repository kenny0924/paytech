package com.paytech.ledger.auto.ext.http.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.AccountingRuleBundleApi;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.test.base.AbstractRuleBundleApiTest;

/**
 * 作废规则草稿：只有编制人、只有 DRAFT；作废后不再阻塞新建、不能提交；编制人重复作废返回当前结果。
 *
 * @see AccountingRuleBundleApi#voidRuleVersion(String, String, String, String)
 */
class VoidRuleVersionTest extends AbstractRuleBundleApiTest {

    /** 编制人作废草稿：VOIDED，指针不变，可再新建版本；作废的版本不能提交。 */
    @Test
    void test_makerVoidsDraft() {
        long versionId = createVersion(version()).assertSuccess(201).id("versionId");

        voidVersion(2, MAKER_ID).assertSuccess(200).assertText("status", RuleVersionStatus.VOIDED.getCode())
            .assertText("makerId", MAKER_ID);

        assertEquals(RuleVersionStatus.VOIDED.getCode(), testMapper.selectRuleVersionStatus(versionId));
        assertPointer(INITIAL_VERSION_ID, 0L);
        submit(2, MAKER_ID).assertRejected(LedgerErrorCode.RULE_VERSION_STATUS_INVALID);
        createVersion(version()).assertSuccess(201).assertText("bundleVer", "3");
    }

    /** 编制人重复作废直接返回当前结果。 */
    @Test
    void test_repeatVoidReturnsCurrent() {
        createVersion(version()).assertSuccess(201);
        voidVersion(2, MAKER_ID).assertSuccess(200);

        voidVersion(2, MAKER_ID).assertSuccess(200).assertText("status", RuleVersionStatus.VOIDED.getCode());
    }

    /** 非编制人不能作废（含已作废版本），版本保持原状态。 */
    @Test
    void test_nonMakerRejected() {
        createVersion(version()).assertSuccess(201);

        voidVersion(2, CHECKER_ID).assertRejected(LedgerErrorCode.RULE_SUBMITTER_NOT_MAKER);
        getVersion(2).assertSuccess(200).assertText("status", RuleVersionStatus.DRAFT.getCode());

        voidVersion(2, MAKER_ID).assertSuccess(200);
        voidVersion(2, CHECKER_ID).assertRejected(LedgerErrorCode.RULE_SUBMITTER_NOT_MAKER);
    }

    /** 非 DRAFT 不能作废：待复核、已生效（初始化第 1 版，编制人 1）与已驳回。 */
    @Test
    void test_nonDraftRejected() {
        createSubmitted(version());
        voidVersion(2, MAKER_ID).assertRejected(LedgerErrorCode.RULE_VERSION_STATUS_INVALID);

        reject(2, CHECKER_ID).assertSuccess(200);
        voidVersion(2, MAKER_ID).assertRejected(LedgerErrorCode.RULE_VERSION_STATUS_INVALID);

        voidVersion(INITIAL_BUNDLE_VER, "1").assertRejected(LedgerErrorCode.RULE_VERSION_STATUS_INVALID);
        assertPointer(INITIAL_VERSION_ID, 0L);
    }

    /** 规则集或版本不存在、操作人 Header 缺失或格式错误。 */
    @Test
    void test_invalidParameter() {
        postAs(PATH + "/UNKNOWN_BUNDLE/versions/1/void", MAKER_ID, null).assertBusinessRejected("UNKNOWN_BUNDLE");
        voidVersion(99, MAKER_ID).assertBusinessRejected("自动记账规则版本不存在");
        createVersion(version()).assertSuccess(201);
        post(versionPath(2) + "/void", null).assertInvalidRequest();
        postAs(versionPath(2) + "/void", "maker", null).assertInvalidRequest();
        getVersion(2).assertSuccess(200).assertText("status", RuleVersionStatus.DRAFT.getCode());
    }
}
