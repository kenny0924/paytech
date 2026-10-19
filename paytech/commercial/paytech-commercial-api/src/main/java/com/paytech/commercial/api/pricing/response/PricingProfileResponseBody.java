package com.paytech.commercial.api.pricing.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 定价画像及其条件。 */
@Getter
@Setter
public class PricingProfileResponseBody {
    /** 画像 ID */
    private String pricingProfileId;

    /** 画像代码 */
    private String pricingProfileCode;

    /** 展示名称 */
    private String profileName;

    /** 定价对象种类 */
    private String profileKind;

    /** Payment Method 代码；非 PAYMENT_METHOD 种类为空 */
    private String paymentMethodCode;

    /** 状态：DRAFT、ACTIVE、RETIRED */
    private String status;

    /** 画像 BCR Root 当前版本，下一次写入的 expectedChangeVersion */
    private Long changeVersion;

    /** 条件行，按维度、值排序 */
    private List<ProfileConditionDetail> conditions;
}
