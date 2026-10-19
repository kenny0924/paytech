package com.paytech.billing.domain.view;

import java.math.BigDecimal;
import java.util.Map;

import com.paytech.billing.api.enums.SourceRuleType;

import lombok.Getter;
import lombok.Setter;

/**
 * 明细行的计算证据：读取接口在事件载荷的行字段之外返回的部分
 * <li>与 {@link BillingFactView#getPayload()} 的 lines 按下标一一对应。</li>
 */
@Getter
@Setter
public class BillingFactLineEvidenceView {
    /** 计算基数（八位小数以内）。 */
    private BigDecimal basisAmount;

    /** 费率；非规则行为空。 */
    private BigDecimal rateValue;

    /** 未按币种精度舍入的金额。 */
    private BigDecimal rawAmount;

    /** 来源规则族；非规则行为空。 */
    private SourceRuleType sourceRuleType;

    /** 来源规则版本 ID；非规则行为空。 */
    private Long sourceRuleVersionId;

    /** 来源 Price ID；可为空。 */
    private Long sourcePriceId;

    /** 来源规则组件 ID；可为空。 */
    private Long sourceRuleComponentId;

    /** 计算证据 JSON 对象；非规则行为空。 */
    private Map<String, Object> sourceEvidence;
}
