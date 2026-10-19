package com.paytech.ledger.auto.ext.http.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.AutoInstructionType;
import com.paytech.ledger.api.enums.RuleOwnerRole;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.test.base.AbstractRuleBundleApiTest;
import com.paytech.ledger.test.builder.RuleVersionRequestBuilder;

/**
 * 创建规则版本的行集校验：非法目录代码、不存在科目、非法余额桶组合、重复行号/行代码与必填字段；被拒时不新增任何版本。
 *
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#createRuleVersion(String, com.paytech.ledger.api.request.CreateRuleVersionRequestBody)
 */
class CreateRuleVersionValidationTest extends AbstractRuleBundleApiTest {

    /** 每例被拒后 Bundle 仍只有初始化第 1 版。 */
    @AfterEach
    void assertNoVersionCreated() {
        assertEquals(1, getBundle(BUNDLE_CODE).assertSuccess(200).json().path("versions").size(), "不应新增版本");
    }

    @Test
    void test_unknownAmountSourceRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(0).setAmountSourceCode("UNKNOWN_SOURCE");

        createVersion(builder).assertBusinessRejected("UNKNOWN_SOURCE");
    }

    @Test
    void test_unknownOwnerRoleRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(1).setOwnerRole("UNKNOWN_ROLE");

        createVersion(builder).assertBusinessRejected("UNKNOWN_ROLE");
    }

    @Test
    void test_unknownInstructionTypeRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(2).setInstructionType("UNKNOWN_INSTRUCTION");

        createVersion(builder).assertBusinessRejected("UNKNOWN_INSTRUCTION");
    }

    @Test
    void test_unknownAccountCodeRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(2).setAccountCode("NOT_EXISTS_ACCOUNT");

        createVersion(builder).assertBusinessRejected("NOT_EXISTS_ACCOUNT");
    }

    /** 渠道清算账户角色只能使用 NONE 桶。 */
    @Test
    void test_channelAccountPendingBucketRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(0).setBalanceBucket(BalanceBucket.PENDING.getCode());

        createVersion(builder).assertBusinessRejected(RuleVersionRequestBuilder.CHANNEL_UNSETTLED_RECEIVABLE);
    }

    /** Client / Local Partner 角色已从规则目录删除：Owner 行只能来自 account 流水。 */
    @Test
    void test_removedOwnerRoleRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(1).setOwnerRole(OwnerType.CLIENT.getCode());

        createVersion(builder).assertBusinessRejected("核算对象角色不在自动记账目录中: " + OwnerType.CLIENT.getCode());
    }

    /** 规则行不得引用 Owner 控制科目，即使核算对象是账簿主体、桶为 NONE。 */
    @Test
    void test_controlAccountRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(1).setAccountCode(StandardAccountCodes.CLIENT_PAYABLE);

        createVersion(builder).assertBusinessRejected("Owner 控制科目");
    }

    /** 总账账户一律 NONE 桶。 */
    @Test
    void test_nonNoneBucketRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(1).setBalanceBucket(BalanceBucket.PENDING.getCode());

        createVersion(builder).assertBusinessRejected("余额桶只能为 NONE");
    }

    /** 行集为空由服务端拒绝。 */
    @Test
    void test_emptyLinesRejected() {
        createVersion(version().clearLines()).assertProblem(400, "BUSINESS_CHECK_FAILED");
    }

    /** 同一指令类型内行号重复。 */
    @Test
    void test_duplicateLineNoRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.addLine(RuleVersionRequestBuilder.line(AutoInstructionType.PAYMENT_SUCCEEDED, 1, "EXTRA_FEE_REVENUE",
            AmountSource.CLIENT_FEE, RuleOwnerRole.BOOK_ENTITY, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            BalanceBucket.NONE, EntrySide.CREDIT, false));

        createVersion(builder).assertBusinessRejected(AutoInstructionType.PAYMENT_SUCCEEDED.getCode());
    }

    /** 同一指令类型内行代码重复。 */
    @Test
    void test_duplicateLineCodeRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.addLine(RuleVersionRequestBuilder.line(AutoInstructionType.PAYMENT_SUCCEEDED, 9, "GROSS_RECEIVABLE",
            AmountSource.CLIENT_FEE, RuleOwnerRole.BOOK_ENTITY, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            BalanceBucket.NONE, EntrySide.CREDIT, false));

        createVersion(builder).assertBusinessRejected("GROSS_RECEIVABLE");
    }

    /** 编制人缺失属于字段校验。 */
    @Test
    void test_missingMakerIdInvalid() {
        createVersion(version().makerId(null)).assertProblem(422, "VALIDATION_ERROR").assertFieldError("makerId");
    }
}
