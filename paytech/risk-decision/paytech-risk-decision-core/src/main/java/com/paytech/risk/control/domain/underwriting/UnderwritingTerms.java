package com.paytech.risk.control.domain.underwriting;

import java.math.BigDecimal;

import lombok.Data;

/** 承保结论字段：观察期、最低保证金要求、结算周期建议、卡组织登记标记与说明。 */
@Data
public class UnderwritingTerms {
    /** 标准模板的观察期天数。 */
    static final int STANDARD_OBSERVATION_DAYS = 30;

    /** 标准模板的观察期笔数。 */
    static final int STANDARD_OBSERVATION_TRANSACTIONS = 100;

    /** 观察期天数；提交必填。 */
    private Integer observationDays;

    /** 观察期笔数；提交必填。 */
    private Integer observationTransactions;

    /** 是否要求最低保证金；提交必填。 */
    private Boolean requireDeposit;

    /** 最低保证金金额；不要求时为空。 */
    private BigDecimal depositAmount;

    /** 最低保证金币种；不要求时为空。 */
    private String depositCurrency;

    /** 到账前是否建议阻塞收款；提交必填。 */
    private Boolean depositBlocksCollection;

    /** 结算周期建议 T+N 的 N；可空。 */
    private Integer suggestedSettlementDays;

    /** 是否需卡组织高风险登记；为空时取输入的卡组织高风险标记。 */
    private Boolean highRiskMcc;

    /** 与默认不同时的理由。 */
    private String highRiskMccReason;

    /** 额度说明。 */
    private String limitNote;

    /** 承保说明。 */
    private String underwritingNote;

    /** 交接备注。 */
    private String handoverNote;

    /**
     * 自动承保的标准模板
     * <li>观察期 30 天 / 100 笔，不要求保证金，不需卡组织登记，结算周期建议为空。</li>
     *
     * @return 新的标准结论
     */
    public static UnderwritingTerms standardTemplate() {
        UnderwritingTerms terms = new UnderwritingTerms();
        terms.setObservationDays(STANDARD_OBSERVATION_DAYS);
        terms.setObservationTransactions(STANDARD_OBSERVATION_TRANSACTIONS);
        terms.setRequireDeposit(Boolean.FALSE);
        terms.setDepositBlocksCollection(Boolean.FALSE);
        terms.setHighRiskMcc(Boolean.FALSE);
        return terms;
    }

    /** 空结论：草稿未填写结论字段时使用。 */
    public static UnderwritingTerms empty() {
        return new UnderwritingTerms();
    }
}
