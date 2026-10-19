package com.paytech.commercial.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.paytech.commercial.api.enums.CommercialPriceErrorCode;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.foundation.common.error.FailException;

/**
 * 价格命中使用的受控事实：支付方式、交易币种与维度事实
 * <li>维度键存在表示事实已知（值为 null 表示已确认不适用，不匹配任何条件）；键缺失表示未知。CURRENCY 维度恒等于交易币种。</li>
 * <li>不可变；只由已知输入构造，不访问数据库。</li>
 */
public final class PricingFacts {
    /** 维度值的规范化格式。 */
    private static final String VALUE_PATTERN = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}";

    private final String paymentMethodCode;

    private final String currency;

    private final Map<PricingDimension, String> dimensions;

    /**
     * 直接由已解析的维度构造。
     *
     * @param paymentMethodCode 支付方式，可空
     * @param currency 交易币种
     * @param dimensions 已知维度事实（值可空）
     */
    public PricingFacts(String paymentMethodCode, String currency, Map<PricingDimension, String> dimensions) {
        this.paymentMethodCode = paymentMethodCode;
        this.currency = Objects.requireNonNull(currency, "currency");
        Map<PricingDimension, String> copy = new EnumMap<>(PricingDimension.class);
        copy.putAll(dimensions);
        copy.put(PricingDimension.CURRENCY, currency);
        this.dimensions = Collections.unmodifiableMap(copy);
    }

    /**
     * 解析外部维度事实：键必须是 PricingDimension 目录代码，值为空或规范化离散值；CURRENCY 给出时必须等于交易币种
     *
     * @param paymentMethodCode 支付方式，可空
     * @param currency 交易币种
     * @param rawDimensions 维度代码到值
     * @return 事实
     * @throws FailException INVALID_PRICING_FACT
     */
    public static PricingFacts parse(String paymentMethodCode, String currency, Map<String, String> rawDimensions) {
        Map<PricingDimension, String> parsed = new EnumMap<>(PricingDimension.class);
        for (Map.Entry<String, String> entry : rawDimensions.entrySet()) {
            PricingDimension dimension = dimension(entry.getKey());
            if (dimension == null) {
                throw new FailException(CommercialPriceErrorCode.INVALID_PRICING_FACT, "未知定价维度: " + entry.getKey());
            }
            String value = entry.getValue();
            if (value != null && !value.matches(VALUE_PATTERN)) {
                throw new FailException(CommercialPriceErrorCode.INVALID_PRICING_FACT, "定价维度值不合法: " + entry.getKey());
            }
            if (dimension == PricingDimension.CURRENCY && !currency.equals(value)) {
                throw new FailException(CommercialPriceErrorCode.INVALID_PRICING_FACT, "CURRENCY 维度与交易币种不一致: " + value);
            }
            parsed.put(dimension, value);
        }
        return new PricingFacts(paymentMethodCode, currency, parsed);
    }

    /** 支付方式，可空。 */
    public String paymentMethodCode() {
        return paymentMethodCode;
    }

    /** 交易币种。 */
    public String currency() {
        return currency;
    }

    /** 维度事实是否已知（含已确认不适用）。 */
    public boolean known(PricingDimension dimension) {
        return dimensions.containsKey(dimension);
    }

    /** 维度事实值；未知或不适用为 null。 */
    public String value(PricingDimension dimension) {
        return dimensions.get(dimension);
    }

    /**
     * 按稳定代码查找维度
     *
     * @param code 维度代码
     * @return 维度，目录外为 null
     */
    public static PricingDimension dimension(String code) {
        for (PricingDimension dimension : PricingDimension.values()) {
            if (dimension.getCode().equals(code)) {
                return dimension;
            }
        }
        return null;
    }
}
