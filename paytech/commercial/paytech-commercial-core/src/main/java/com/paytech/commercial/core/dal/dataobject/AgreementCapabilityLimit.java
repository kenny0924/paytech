package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.CapabilityLimitCode;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * t_agreement_capability_limit：开通项约束，一值一行。
 * <li>同码多行按 OR、异码按 AND；随开通项冻结，不单独审计；金额限额归风控决策，不在此表。</li>
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AgreementCapabilityLimit {
    /** 约束行的内部唯一标识 */
    private Long agreementCapabilityLimitId;

    /** 所属开通项 */
    private Long agreementCapabilityId;

    /** 约束类别 */
    private CapabilityLimitCode limitCode;

    /**
     * 约束的规范化值
     * <li>CURRENCY 为 ISO 4217，CONSUMER_COUNTRY 为 ISO 3166-1 alpha-2，REQUIRE_3DS 固定为 1；与前两列组成 uk_agreement_capability_limit。</li>
     */
    private String limitValue;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
