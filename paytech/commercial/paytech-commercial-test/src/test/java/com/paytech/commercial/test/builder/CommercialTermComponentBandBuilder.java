package com.paytech.commercial.test.builder;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_commercial_term_component_band 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class CommercialTermComponentBandBuilder {
    /** 默认档位 ID */
    public static final long TERM_BAND_ID = 930000000000101L;

    private final CommercialTermComponentBand commercialTermComponentBand = new CommercialTermComponentBand();

    private CommercialTermComponentBandBuilder() {
    }

    /**
     * 默认组件的第一档 [0, 1000) 费率 2%。
     *
     * @return 新 Builder
     */
    public static CommercialTermComponentBandBuilder template() {
        return new CommercialTermComponentBandBuilder()
            .termBandId(TERM_BAND_ID)
            .termComponentId(CommercialTermComponentBuilder.TERM_COMPONENT_ID)
            .bandNo(1)
            .lowerBound(BigDecimal.ZERO)
            .upperBound(new BigDecimal("1000"))
            .rate(new BigDecimal("0.02"))
            .fixedAmount(null)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置档位标识。 */
    public CommercialTermComponentBandBuilder termBandId(Long value) {
        commercialTermComponentBand.setTermBandId(value);
        return this;
    }

    /** 设置所属组件。 */
    public CommercialTermComponentBandBuilder termComponentId(Long value) {
        commercialTermComponentBand.setTermComponentId(value);
        return this;
    }

    /** 设置组件内从 1 连续的档位顺序。 */
    public CommercialTermComponentBandBuilder bandNo(Integer value) {
        commercialTermComponentBand.setBandNo(value);
        return this;
    }

    /** 设置包含下界。 */
    public CommercialTermComponentBandBuilder lowerBound(BigDecimal value) {
        commercialTermComponentBand.setLowerBound(value);
        return this;
    }

    /** 设置不包含上界；末档为空。 */
    public CommercialTermComponentBandBuilder upperBound(BigDecimal value) {
        commercialTermComponentBand.setUpperBound(value);
        return this;
    }

    /** 设置档位小数费率。 */
    public CommercialTermComponentBandBuilder rate(BigDecimal value) {
        commercialTermComponentBand.setRate(value);
        return this;
    }

    /** 设置档位固定金额。 */
    public CommercialTermComponentBandBuilder fixedAmount(BigDecimal value) {
        commercialTermComponentBand.setFixedAmount(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public CommercialTermComponentBandBuilder createTime(Instant value) {
        commercialTermComponentBand.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public CommercialTermComponentBandBuilder updateTime(Instant value) {
        commercialTermComponentBand.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public CommercialTermComponentBand build() {
        return commercialTermComponentBand;
    }
}
