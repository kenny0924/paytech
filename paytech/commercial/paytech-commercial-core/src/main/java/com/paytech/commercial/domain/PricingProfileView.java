package com.paytech.commercial.domain;

import java.util.Comparator;
import java.util.List;

import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.enums.PricingProfileStatus;

import lombok.Getter;

/**
 * 定价画像组合结果：画像、条件行与 BCR Root 版本三表组合，供画像列表与画像写入响应使用
 * <li>不携带 DataObject，供 app 入站适配器转换为 ResponseBody。</li>
 */
@Getter
public class PricingProfileView {
    /** 画像 ID */
    private final long pricingProfileId;

    /** 画像代码 */
    private final String pricingProfileCode;

    /** 展示名称 */
    private final String profileName;

    /** 定价对象种类 */
    private final PricingProfileKind profileKind;

    /** Payment Method 代码；非 PAYMENT_METHOD 为空 */
    private final String paymentMethodCode;

    /** 状态 */
    private final PricingProfileStatus status;

    /**
     * 画像 BCR Root 当前版本
     * <li>未接入 BCR 的历史画像为空，不能经写入接口修改。</li>
     */
    private final Long changeVersion;

    /** 条件行，按维度 Code、值排序 */
    private final List<Condition> conditions;

    /**
     * 由已读取或本次保存后的行构造
     *
     * @param profile 画像
     * @param conditions 条件行
     * @param changeVersion Root 当前版本
     */
    public PricingProfileView(PricingProfile profile, List<PricingProfileCond> conditions, Long changeVersion) {
        this.pricingProfileId = profile.getPricingProfileId();
        this.pricingProfileCode = profile.getPricingProfileCode();
        this.profileName = profile.getProfileName();
        this.profileKind = profile.getProfileKind();
        this.paymentMethodCode = profile.getPaymentMethodCode();
        this.status = profile.getStatus();
        this.changeVersion = changeVersion;
        this.conditions = conditions.stream()
            .sorted(Comparator.comparing((PricingProfileCond cond) -> cond.getDimensionCode().getCode())
                .thenComparing(PricingProfileCond::getConditionValue))
            .map(cond -> new Condition(cond.getDimensionCode(), cond.getConditionValue())).toList();
    }

    /** 画像的一个等值条件。 */
    @Getter
    public static class Condition {
        /** 定价维度 */
        private final PricingDimension dimensionCode;

        /** 规范化值 */
        private final String conditionValue;

        /** 构造条件。 */
        Condition(PricingDimension dimensionCode, String conditionValue) {
            this.dimensionCode = dimensionCode;
            this.conditionValue = conditionValue;
        }
    }
}
