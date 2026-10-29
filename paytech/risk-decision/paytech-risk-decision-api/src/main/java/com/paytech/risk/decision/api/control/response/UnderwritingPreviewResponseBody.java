package com.paytech.risk.decision.api.control.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 自动承保判定与估算敞口预览。 */
@Getter
@Setter
public class UnderwritingPreviewResponseBody {

    /** 判定使用的 ACTIVE 规则版本号；没有 ACTIVE 规则为空。 */
    private Integer ruleVersionNo;

    /** 全部检查通过时为 true，可一键自动承保。 */
    private Boolean autoEligible;

    /** 各项检查，顺序固定。 */
    private List<UnderwritingCheckBody> checks;

    /**
     * 输入流水币种在规则中的月流水上限（含），币种主单位十进制字符串
     * <li>没有 ACTIVE 规则、未申报流水或该币种无阈值时为空；页面用于展示"预计月流水 ≤ 阈值"。</li>
     */
    private String thresholdAmount;

    /** 上限的币种，等于输入的流水币种；上限为空时为空。 */
    private String thresholdCurrency;

    /** 估算敞口；无法估算为空。 */
    private UnderwritingExposureBody estimatedExposure;

    /** 无法估算的原因；可估算时为空。 */
    private String exposureUnavailableReason;

    /** 复核级别：CRR 缺失为 MEDIUM。 */
    private String reviewLevel;
}
