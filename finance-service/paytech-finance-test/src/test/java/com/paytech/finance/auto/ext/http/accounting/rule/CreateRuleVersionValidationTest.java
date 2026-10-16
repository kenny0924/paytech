package com.paytech.finance.auto.ext.http.accounting.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.constant.StandardAccountCodes;
import com.paytech.finance.accounting.core.enums.AmountSource;
import com.paytech.finance.accounting.core.enums.AutoInstructionType;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.core.enums.OwnerRole;
import com.paytech.finance.test.base.AbstractRuleBundleApiTest;
import com.paytech.finance.test.builder.RuleVersionRequestBuilder;

/**
 * 创建规则版本的行集校验：非法目录代码、不存在科目、非法余额桶组合、重复行号/行代码与必填字段；被拒时不新增任何版本。
 *
 * @see com.paytech.finance.api.accounting.AccountingRuleBundleApi#createRuleVersion(String, com.paytech.finance.api.accounting.request.CreateRuleVersionRequestBody)
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

    /** Partner 应付只允许 PENDING/AVAILABLE/LOCKED。 */
    @Test
    void test_partnerPayableNoneBucketRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(7).setBalanceBucket(BalanceBucket.NONE.getCode());

        createVersion(builder).assertBusinessRejected(StandardAccountCodes.PARTNER_PAYABLE);
    }

    /** Client 应付只允许 PENDING/AVAILABLE/LOCKED（账户身份矩阵）。 */
    @Test
    void test_clientPayableNoneBucketRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.line(1).setBalanceBucket(BalanceBucket.NONE.getCode());

        createVersion(builder).assertBusinessRejected(StandardAccountCodes.CLIENT_PAYABLE);
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
            AmountSource.CLIENT_FEE, OwnerRole.BOOK_ENTITY, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            BalanceBucket.NONE, EntrySide.CREDIT, false));

        createVersion(builder).assertBusinessRejected(AutoInstructionType.PAYMENT_SUCCEEDED.getCode());
    }

    /** 同一指令类型内行代码重复。 */
    @Test
    void test_duplicateLineCodeRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.addLine(RuleVersionRequestBuilder.line(AutoInstructionType.PAYMENT_SUCCEEDED, 9, "GROSS_RECEIVABLE",
            AmountSource.CLIENT_FEE, OwnerRole.BOOK_ENTITY, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            BalanceBucket.NONE, EntrySide.CREDIT, false));

        createVersion(builder).assertBusinessRejected("GROSS_RECEIVABLE");
    }

    /** 编制人缺失属于字段校验。 */
    @Test
    void test_missingMakerIdInvalid() {
        createVersion(version().makerId(null)).assertProblem(422, "VALIDATION_ERROR").assertFieldError("makerId");
    }
}
