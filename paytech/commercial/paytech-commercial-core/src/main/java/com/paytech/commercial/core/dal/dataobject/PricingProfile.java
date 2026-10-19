package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.domain.enums.PricingProfileStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_pricing_profile：全局可复用的定价画像。
 * <li>DRAFT 可编辑；ACTIVE 后 Code、种类、Payment Method 与条件永久冻结；RETIRED 只阻止新引用。</li>
 */
@Getter
@Setter
public class PricingProfile {
    /** 画像内部标识 */
    private Long pricingProfileId;

    /** 全局唯一稳定代码，创建后不可修改、不可复用 */
    private String pricingProfileCode;

    /** 展示名称 */
    private String profileName;

    /** 定价对象种类 */
    private PricingProfileKind profileKind;

    /** profile_kind=PAYMENT_METHOD 时必填的 Payment Method 代码，其他种类为空 */
    private String paymentMethodCode;

    /** 生命周期状态 */
    private PricingProfileStatus status;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
