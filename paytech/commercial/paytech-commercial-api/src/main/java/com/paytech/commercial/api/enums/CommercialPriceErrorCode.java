package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 价格命中接口（POST /commercial-prices/v1/resolutions）需要调用方区分处理的错误（technique.md §2.4，stage3 §2.3）
 * <li>消费者 billing：事实缺失时保持经济事实待算并重试取数；无命中与非法事实转人工；歧义或父链冲突停止计算并告警。</li>
 */
public enum CommercialPriceErrorCode implements CodeMessageEnum {
    /** 候选画像使用的维度在支付事实中未知，422；不得借宽泛画像把未知当作不匹配。 */
    PRICING_FACTS_INCOMPLETE("PRICING_FACTS_INCOMPLETE", "定价事实不完整"),

    /** 固定版本、准确 OE、币种与支付方式下没有命中的价格，422。 */
    PRICE_NOT_FOUND("PRICE_NOT_FOUND", "没有命中的价格"),

    /** 支付事实包含目录外的维度代码或不合法的值，422。 */
    INVALID_PRICING_FACT("INVALID_PRICING_FACT", "定价事实不合法"),

    /** 同具体度同优先级仍有多条命中，或版本、方案、合同、Owner、OE 父链与请求冲突，409。 */
    PRICE_AMBIGUOUS("PRICE_AMBIGUOUS", "价格命中存在歧义或父链冲突");

    private final String code;

    private final String message;

    CommercialPriceErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 稳定错误码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 默认安全提示。 */
    @Override
    public String getMessage() {
        return message;
    }
}
