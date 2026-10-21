package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.PricingDimension;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * t_agreement_capability_limit：开通项接受条件，一值一行。
 * <li>同维度多行按 OR、异维度按 AND，未写维度不限；随开通项冻结，不单独审计；金额限额归风控决策，不在此表。</li>
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AgreementCapabilityLimit {
    /** 条件行的内部唯一标识 */
    private Long agreementCapabilityLimitId;

    /** 所属开通项 */
    private Long agreementCapabilityId;

    /** 条件维度，须在该可开通项可配维度内 */
    private PricingDimension limitCode;

    /**
     * 该维度的一个规范化取值
     * <li>CURRENCY 为 ISO 4217，国家类为 ISO 3166-1 alpha-2，其他为大写稳定代码；与前两列组成 uk_agreement_capability_limit。</li>
     */
    private String limitValue;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
