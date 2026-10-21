package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.ProductCode;

import lombok.Getter;
import lombok.Setter;

/**
 * t_product_fee_event：L2 计费事件目录，一个事件可在哪类条款下收费一值一行。
 * <li>fee_event_code 是组件 trigger_code 的值域，term_type 是方案 term_type 的值域；挂接开通项由运营维护，写入以 changeVersion 做 CAS，
 * BCR 聚合根 PRODUCT_CATALOG。</li>
 */
@Getter
@Setter
public class ProductFeeEvent {
    /** 计费事件目录行的内部唯一标识 */
    private Long productFeeEventId;

    /** 所属产品的稳定代码 */
    private ProductCode productCode;

    /**
     * 触发收费的业务动作代码
     * <li>开放目录，稳定项见 CommercialCodes.FeeEvent；与 termType 组成 uk_product_fee_event。</li>
     */
    private String feeEventCode;

    /**
     * 该事件可收费的商业条款类型
     * <li>开放目录，稳定项见 CommercialCodes.TermType。</li>
     */
    private String termType;

    /**
     * 对应的 billing 事实类型代码
     * <li>开放目录，由 billing 拥有；稳定项见 CommercialCodes.BillingFactType。</li>
     */
    private String billingFactType;

    /**
     * 挂接的开通项种类
     * <li>为空表示无需开通的基础产品事件。</li>
     */
    private CapabilityKind capabilityKind;

    /**
     * 挂接的功能开通项代码
     * <li>仅 capabilityKind=FEATURE 非空，指向同产品 FEATURE 可开通项；其他种类为空，表示任一该种类开通项。</li>
     */
    private String capabilityCode;

    /** 目录行乐观锁版本，初始 1，每次写入加一 */
    private Long changeVersion;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
