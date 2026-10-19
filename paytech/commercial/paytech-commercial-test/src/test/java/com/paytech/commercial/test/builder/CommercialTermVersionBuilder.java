package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.CommercialTermRoundingMode;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_commercial_term_version 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class CommercialTermVersionBuilder {
    /** 默认版本 ID */
    public static final long TERM_VERSION_ID = 930000000000071L;

    private final CommercialTermVersion commercialTermVersion = new CommercialTermVersion();

    private CommercialTermVersionBuilder() {
    }

    /**
     * 默认方案的第 1 版，DRAFT、未占槽、BLENDED、不含税。
     *
     * @return 新 Builder
     */
    public static CommercialTermVersionBuilder template() {
        return new CommercialTermVersionBuilder()
            .termVersionId(TERM_VERSION_ID)
            .termPlanId(CommercialTermPlanBuilder.TERM_PLAN_ID)
            .agreementId(AgreementBuilder.AGREEMENT_ID)
            .versionNo(1L)
            .status(CommercialTermVersionStatus.DRAFT)
            .activeSlot(null)
            .effectiveFrom(Instant.parse("2026-09-01T00:00:00Z"))
            .effectiveTo(null)
            .roundingMode(CommercialTermRoundingMode.HALF_UP)
            .pricingModel(PricingModel.BLENDED)
            .feeTaxTreatment(FeeTaxTreatment.EXCLUSIVE)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置版本标识。 */
    public CommercialTermVersionBuilder termVersionId(Long value) {
        commercialTermVersion.setTermVersionId(value);
        return this;
    }

    /** 设置所属方案。 */
    public CommercialTermVersionBuilder termPlanId(Long value) {
        commercialTermVersion.setTermPlanId(value);
        return this;
    }

    /** 设置版本冻结引用的合同。 */
    public CommercialTermVersionBuilder agreementId(Long value) {
        commercialTermVersion.setAgreementId(value);
        return this;
    }

    /** 设置方案内版本序号。 */
    public CommercialTermVersionBuilder versionNo(Long value) {
        commercialTermVersion.setVersionNo(value);
        return this;
    }

    /** 设置版本状态。 */
    public CommercialTermVersionBuilder status(CommercialTermVersionStatus value) {
        commercialTermVersion.setStatus(value);
        return this;
    }

    /** 设置1 或 NULL：ACTIVE 为 1，其他状态为空。 */
    public CommercialTermVersionBuilder activeSlot(Integer value) {
        commercialTermVersion.setActiveSlot(value);
        return this;
    }

    /** 设置生效区间 UTC 起点（含）。 */
    public CommercialTermVersionBuilder effectiveFrom(Instant value) {
        commercialTermVersion.setEffectiveFrom(value);
        return this;
    }

    /** 设置生效区间 UTC 终点（不含）；空为未预定结束。 */
    public CommercialTermVersionBuilder effectiveTo(Instant value) {
        commercialTermVersion.setEffectiveTo(value);
        return this;
    }

    /** 设置金额舍入规则。 */
    public CommercialTermVersionBuilder roundingMode(CommercialTermRoundingMode value) {
        commercialTermVersion.setRoundingMode(value);
        return this;
    }

    /** 设置定价模型；本期只接受 BLENDED。 */
    public CommercialTermVersionBuilder pricingModel(PricingModel value) {
        commercialTermVersion.setPricingModel(value);
        return this;
    }

    /** 设置费用金额是否含税。 */
    public CommercialTermVersionBuilder feeTaxTreatment(FeeTaxTreatment value) {
        commercialTermVersion.setFeeTaxTreatment(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public CommercialTermVersionBuilder createTime(Instant value) {
        commercialTermVersion.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public CommercialTermVersionBuilder updateTime(Instant value) {
        commercialTermVersion.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public CommercialTermVersion build() {
        return commercialTermVersion;
    }
}
