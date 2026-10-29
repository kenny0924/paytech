package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 承保结论字段（页面填写）
 * <li>草稿可部分缺省；提交时观察期、保证金三项与高风险 MCC 标记必填，联动规则由 risk-decision 校验。</li>
 */
@Getter
@Setter
public class UnderwritingDecisionBody {

    /** 观察期天数 1—365；提交必填，页面默认 30。 */
    @Min(1)
    @Max(365)
    private Integer observationDays;

    /** 观察期交易笔数 1—100000；提交必填，页面默认 100。 */
    @Min(1)
    @Max(100000)
    private Integer observationTransactions;

    /** 是否要求最低保证金；提交必填，页面默认 false。 */
    private Boolean requireDeposit;

    /**
     * 最低保证金金额，币种主单位十进制字符串
     * <li>requireDeposit=true 时必填且大于 0；不要求时须为空。</li>
     */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?")
    private String depositAmount;

    /** 最低保证金 ISO 4217 币种；requireDeposit=true 时必填，不要求时须为空。 */
    @Pattern(regexp = "[A-Z]{3}")
    private String depositCurrency;

    /** 保证金到账前是否建议阻塞收款；只有 requireDeposit=true 时可为 true；提交必填。 */
    private Boolean depositBlocksCollection;

    /**
     * 结算周期建议 T+N 的 N，0—90
     * <li>为空时估算敞口按 0 计并标注。</li>
     */
    @Min(0)
    @Max(90)
    private Integer suggestedSettlementDays;

    /**
     * 是否需卡组织高风险登记
     * <li>默认等于输入的 networkHighRiskMcc；与默认不同时 highRiskMccReason 必填。</li>
     */
    private Boolean highRiskMcc;

    /** highRiskMcc 与默认值不同时的理由，最长 500 字符。 */
    @Size(max = 500)
    private String highRiskMccReason;

    /** 额度说明，最长 500 字符（不设逐 Client 额度）。 */
    @Size(max = 500)
    private String limitNote;

    /** 承保说明：交付周期与拒付暴露评估及其他判断，最长 2000 字符。 */
    @Size(max = 2000)
    private String underwritingNote;

    /** 交接给商务的备注，最长 500 字符。 */
    @Size(max = 500)
    private String handoverNote;
}
