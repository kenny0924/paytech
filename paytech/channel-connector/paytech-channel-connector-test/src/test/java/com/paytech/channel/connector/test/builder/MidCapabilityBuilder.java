package com.paytech.channel.connector.test.builder;

import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.enums.CapabilityStatus;

/**
 * MID 能力条件组的测试构造：默认 CARD / * / * / AED / 5734、ENABLED；只组装对象，不访问数据库
 * <li>主键、MID 与时间由 ChannelDataMocker 插入时填写。</li>
 */
public final class MidCapabilityBuilder {
    private final MidCapability scope = new MidCapability();

    /** 默认条件组。 */
    public static MidCapabilityBuilder template() {
        return new MidCapabilityBuilder().paymentMethod("CARD").scheme("*").country("*").currency("AED").mcc("5734")
            .status(CapabilityStatus.ENABLED);
    }

    /** 支付方式目录代码。 */
    public MidCapabilityBuilder paymentMethod(String paymentMethodCode) {
        scope.setPaymentMethodCode(paymentMethodCode);
        return this;
    }

    /** 体系代码或 *。 */
    public MidCapabilityBuilder scheme(String schemeCode) {
        scope.setSchemeCode(schemeCode);
        return this;
    }

    /** 国家代码或 *。 */
    public MidCapabilityBuilder country(String countryCode) {
        scope.setCountryCode(countryCode);
        return this;
    }

    /** 币种或 *。 */
    public MidCapabilityBuilder currency(String currency) {
        scope.setCurrency(currency);
        return this;
    }

    /** 四位 MCC。 */
    public MidCapabilityBuilder mcc(String mccCode) {
        scope.setMccCode(mccCode);
        return this;
    }

    /** 条件组状态。 */
    public MidCapabilityBuilder status(CapabilityStatus status) {
        scope.setCapabilityStatus(status);
        return this;
    }

    /** 本次构造的条件组。 */
    public MidCapability build() {
        return scope;
    }
}
