package com.paytech.ledger.test.builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.AmountSource;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.api.enums.RuleOwnerRole;
import com.paytech.ledger.api.request.CreateRuleVersionRequestBody;
import com.paytech.ledger.api.request.RuleLineRequestBody;
import com.paytech.ledger.core.enums.EntrySide;

/**
 * 创建规则版本请求的模板：默认行集与初始化 DML 版本 5101 的 12 条标准规则行逐值相同
 * <li>用例通过 clearLines/addLine/removeLine/line 覆盖场景差异；build 不修复刻意设置的非法值。</li>
 */
public final class RuleVersionRequestBuilder {
    /** 渠道待清算应收科目。 */
    public static final String CHANNEL_UNSETTLED_RECEIVABLE = "CHANNEL_UNSETTLED_RECEIVABLE";

    /** Client 手续费收入科目。 */
    public static final String CLIENT_FEE_REVENUE = "CLIENT_FEE_REVENUE";

    /** 应交税费科目。 */
    public static final String TAX_PAYABLE = "TAX_PAYABLE";

    /** 渠道成本费用科目。 */
    public static final String CHANNEL_FEE_EXPENSE = "CHANNEL_FEE_EXPENSE";

    /** 渠道费用预提科目。 */
    public static final String CHANNEL_FEE_ACCRUAL = "CHANNEL_FEE_ACCRUAL";

    /** Partner 分润费用科目。 */
    public static final String PARTNER_SHARE_EXPENSE = "PARTNER_SHARE_EXPENSE";

    /** 平台服务费费用科目。 */
    public static final String PLATFORM_SERVICE_FEE_EXPENSE = "PLATFORM_SERVICE_FEE_EXPENSE";

    /** 平台服务费应付科目。 */
    public static final String PLATFORM_SERVICE_PAYABLE = "PLATFORM_SERVICE_PAYABLE";

    /** 平台服务费应收科目。 */
    public static final String PLATFORM_SERVICE_RECEIVABLE = "PLATFORM_SERVICE_RECEIVABLE";

    /** 平台服务费收入科目。 */
    public static final String PLATFORM_SERVICE_REVENUE = "PLATFORM_SERVICE_REVENUE";

    private final CreateRuleVersionRequestBody body = new CreateRuleVersionRequestBody();

    private RuleVersionRequestBuilder() {
    }

    /** 标准 12 行模板。 */
    public static RuleVersionRequestBuilder template(String makerId, Instant effectiveFrom) {
        RuleVersionRequestBuilder builder = new RuleVersionRequestBuilder();
        builder.body.setMakerId(makerId);
        builder.body.setEffectiveFrom(effectiveFrom);
        builder.body.setLines(new ArrayList<>(standardLines()));
        return builder;
    }

    /** 初始化 DML 版本 5101 的 10 条规则行（Owner 行 2、8 已删除），按指令类型、行号排序。 */
    public static List<RuleLineRequestBody> standardLines() {
        AutoInstructionType payment = AutoInstructionType.PAYMENT_SUCCEEDED;
        AutoInstructionType partner = AutoInstructionType.PLATFORM_SERVICE_CHARGE_PARTNER;
        AutoInstructionType platform = AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM;
        return List.of(
            line(payment, 1, "GROSS_RECEIVABLE", AmountSource.GROSS_AMOUNT, RuleOwnerRole.CHANNEL_ACCOUNT,
                CHANNEL_UNSETTLED_RECEIVABLE, BalanceBucket.NONE, EntrySide.DEBIT, true),
            line(payment, 3, "CLIENT_FEE_REVENUE", AmountSource.CLIENT_FEE, RuleOwnerRole.BOOK_ENTITY,
                CLIENT_FEE_REVENUE, BalanceBucket.NONE, EntrySide.CREDIT, true),
            line(payment, 4, "SELLER_TAX_PAYABLE", AmountSource.SELLER_TAX, RuleOwnerRole.TAX_AUTHORITY, TAX_PAYABLE,
                BalanceBucket.NONE, EntrySide.CREDIT, false),
            line(payment, 5, "CHANNEL_COST_EXPENSE", AmountSource.CHANNEL_COST, RuleOwnerRole.BOOK_ENTITY,
                CHANNEL_FEE_EXPENSE, BalanceBucket.NONE, EntrySide.DEBIT, false),
            line(payment, 6, "CHANNEL_COST_ACCRUAL", AmountSource.CHANNEL_COST, RuleOwnerRole.CHANNEL_ACCOUNT,
                CHANNEL_FEE_ACCRUAL, BalanceBucket.NONE, EntrySide.CREDIT, false),
            line(payment, 7, "PARTNER_SHARE_EXPENSE", AmountSource.PARTNER_SHARE, RuleOwnerRole.BOOK_ENTITY,
                PARTNER_SHARE_EXPENSE, BalanceBucket.NONE, EntrySide.DEBIT, false),
            line(partner, 1, "SERVICE_FEE_EXPENSE", AmountSource.PLATFORM_SERVICE_CHARGE, RuleOwnerRole.BOOK_ENTITY,
                PLATFORM_SERVICE_FEE_EXPENSE, BalanceBucket.NONE, EntrySide.DEBIT, false),
            line(partner, 2, "SERVICE_PAYABLE", AmountSource.PLATFORM_SERVICE_CHARGE,
                RuleOwnerRole.PLATFORM_FINANCE_ENTITY, PLATFORM_SERVICE_PAYABLE, BalanceBucket.NONE, EntrySide.CREDIT,
                false),
            line(platform, 1, "SERVICE_RECEIVABLE", AmountSource.PLATFORM_SERVICE_CHARGE, RuleOwnerRole.TRANSACTION_OE,
                PLATFORM_SERVICE_RECEIVABLE, BalanceBucket.NONE, EntrySide.DEBIT, false),
            line(platform, 2, "SERVICE_REVENUE", AmountSource.PLATFORM_SERVICE_CHARGE, RuleOwnerRole.BOOK_ENTITY,
                PLATFORM_SERVICE_REVENUE, BalanceBucket.NONE, EntrySide.CREDIT, false));
    }

    /** 以枚举构造一条规则行。 */
    public static RuleLineRequestBody line(AutoInstructionType instructionType, int lineNo, String lineCode,
        AmountSource amountSource, RuleOwnerRole ownerRole, String accountCode, BalanceBucket bucket, EntrySide entrySide,
        boolean required) {
        RuleLineRequestBody line = new RuleLineRequestBody();
        line.setInstructionType(instructionType.getCode());
        line.setLineNo(lineNo);
        line.setLineCode(lineCode);
        line.setAmountSourceCode(amountSource.getCode());
        line.setOwnerRole(ownerRole.getCode());
        line.setAccountCode(accountCode);
        line.setBalanceBucket(bucket.getCode());
        line.setEntrySide(entrySide.getCode());
        line.setRequiredFlag(required);
        return line;
    }

    /** 清空行集。 */
    public RuleVersionRequestBuilder clearLines() {
        body.getLines().clear();
        return this;
    }

    /** 追加规则行。 */
    public RuleVersionRequestBuilder addLine(RuleLineRequestBody... lines) {
        body.getLines().addAll(List.of(lines));
        return this;
    }

    /** 按下标移除一条规则行。 */
    public RuleVersionRequestBuilder removeLine(int index) {
        body.getLines().remove(index);
        return this;
    }

    /** 取得指定下标的规则行以便覆盖字段。 */
    public RuleLineRequestBody line(int index) {
        return body.getLines().get(index);
    }

    /** 覆盖编制人。 */
    public RuleVersionRequestBuilder makerId(String makerId) {
        body.setMakerId(makerId);
        return this;
    }

    /** 返回请求体。 */
    public CreateRuleVersionRequestBody build() {
        return body;
    }
}
