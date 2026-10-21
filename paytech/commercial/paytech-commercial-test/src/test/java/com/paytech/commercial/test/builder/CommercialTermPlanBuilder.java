package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_commercial_term_plan 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class CommercialTermPlanBuilder {
    /** 默认方案 ID */
    public static final long TERM_PLAN_ID = 930000000000061L;

    /** 默认方案编号 */
    public static final String PLAN_CODE = "WRITE-PLAN-1";

    private final CommercialTermPlan commercialTermPlan = new CommercialTermPlan();

    private CommercialTermPlanBuilder() {
    }

    /**
     * 默认合同的 CLIENT_PRICING 方案，DRAFT。
     *
     * @return 新 Builder
     */
    public static CommercialTermPlanBuilder template() {
        return new CommercialTermPlanBuilder()
            .termPlanId(TERM_PLAN_ID)
            .planCode(PLAN_CODE)
            .termType(CommercialCodes.TermType.CLIENT_PRICING)
            .ownerType(CommercialTermOwnerType.CLIENT)
            .agreementId(AgreementBuilder.AGREEMENT_ID)
            .entityNo(AgreementBuilder.ENTITY_NO)
            .clientId(AgreementBuilder.CLIENT_ID)
            .localPartnerId(null)
            .status(CommercialTermPlanStatus.DRAFT)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置方案标识。 */
    public CommercialTermPlanBuilder termPlanId(Long value) {
        commercialTermPlan.setTermPlanId(value);
        return this;
    }

    /** 设置方案唯一稳定业务编号。 */
    public CommercialTermPlanBuilder planCode(String value) {
        commercialTermPlan.setPlanCode(value);
        return this;
    }

    /** 设置商业条款类型；开放目录，值域为 t_product_fee_event.term_type。 */
    public CommercialTermPlanBuilder termType(String value) {
        commercialTermPlan.setTermType(value);
        return this;
    }

    /** 设置CLIENT_PRICING 所属合同。 */
    public CommercialTermPlanBuilder agreementId(Long value) {
        commercialTermPlan.setAgreementId(value);
        return this;
    }

    /** 设置CLIENT_PRICING 的准确 OE。 */
    public CommercialTermPlanBuilder entityNo(String value) {
        commercialTermPlan.setEntityNo(value);
        return this;
    }

    /** 设置CLIENT_PRICING 的 Client。 */
    public CommercialTermPlanBuilder clientId(Long value) {
        commercialTermPlan.setClientId(value);
        return this;
    }

    /** 设置Partner 类方案的 Local Partner。 */
    public CommercialTermPlanBuilder localPartnerId(Long value) {
        commercialTermPlan.setLocalPartnerId(value);
        return this;
    }

    /** 设置方案 Owner 类型。 */
    public CommercialTermPlanBuilder ownerType(CommercialTermOwnerType value) {
        commercialTermPlan.setOwnerType(value);
        return this;
    }

    /** 设置模板显示名。 */
    public CommercialTermPlanBuilder templateName(String value) {
        commercialTermPlan.setTemplateName(value);
        return this;
    }

    /** 设置方案状态。 */
    public CommercialTermPlanBuilder status(CommercialTermPlanStatus value) {
        commercialTermPlan.setStatus(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public CommercialTermPlanBuilder createTime(Instant value) {
        commercialTermPlan.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public CommercialTermPlanBuilder updateTime(Instant value) {
        commercialTermPlan.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public CommercialTermPlan build() {
        return commercialTermPlan;
    }
}
