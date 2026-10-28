package com.paytech.commercial.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * t_agreement_capability：合同在准确 OE 下的开通项与保证金条款。
 * <li>所属产品必须已订阅、capability_code 引用时须为 ACTIVE、entity_no 须被合同覆盖；应用校验，不建 CHECK/FK。</li>
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AgreementCapability {
    /** 开通项行的内部唯一标识 */
    private Long agreementCapabilityId;

    /** 所属合同 */
    private Long agreementId;

    /**
     * 开通项适用的准确 OE
     * <li>CLIENT 合同等于合同 entity_no，PARTNER 合同须在合同覆盖 OE 内；与 agreementId、capabilityCode 组成 uk_agreement_capability。</li>
     */
    private String entityNo;

    /**
     * 开通的可开通项代码
     * <li>开放目录，取值来自 t_product_capability.capability_code。</li>
     */
    private String capabilityCode;

    /**
     * 合同成员资格
     * <li>ACTIVE 为纳入合同（价格须覆盖），DISABLED 为运营关闭。</li>
     */
    private AgreementCapabilityStatus status;

    /**
     * 执行状态
     * <li>status=ACTIVE 时必须非空，DISABLED 时为空（应用校验）；Checkout 只认 ENABLED。</li>
     */
    private AgreementCapabilityExecutionStatus executionStatus;

    /** 开通生效区间的 UTC 微秒起点，包含该时刻 */
    private Instant effectiveFrom;

    /**
     * 开通生效区间的 UTC 微秒终点，不包含该时刻
     * <li>为空表示未预定结束。</li>
     */
    private Instant effectiveTo;

    /**
     * 滚动保证金按交易金额计提的小数比例
     * <li>取值 0 至 1；无保证金时为空，与 rollingReserveDays 成组为空或非空。</li>
     */
    private BigDecimal rollingReserveRate;

    /**
     * 滚动保证金的持有天数
     * <li>与 rollingReserveRate 成组为空或非空；执行归 account / risk。</li>
     */
    private Integer rollingReserveDays;

    /** 是否要求 3DS 认证 */
    private Boolean require3ds;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
