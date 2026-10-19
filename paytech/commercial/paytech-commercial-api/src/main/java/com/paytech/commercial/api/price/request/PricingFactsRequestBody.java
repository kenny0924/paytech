package com.paytech.commercial.api.price.request;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 受控定价事实：支付方式与维度事实
 * <li>dimensions 的键为 PricingDimension 目录代码（14 值），目录外的键返回 422 INVALID_PRICING_FACT。</li>
 * <li>键存在且值为 null 表示已确认该维度不适用；键缺失表示事实未知，候选画像用到该维度时返回 422 PRICING_FACTS_INCOMPLETE。</li>
 * <li>CURRENCY 维度取请求币种，给出时必须与请求币种一致。</li>
 */
@Getter
@Setter
public class PricingFactsRequestBody {
    /** 支付方式稳定代码，与画像绑定的 Payment Method 精确匹配；非支付方式定价（出款、换汇等）为空。 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "paymentMethodCode 格式不正确")
    private String paymentMethodCode;

    /** 维度事实，键为维度代码、值为规范化离散值或 null。 */
    @NotNull(message = "dimensions 必填")
    @Size(max = 32, message = "dimensions 最多 32 项")
    private Map<String, String> dimensions;
}
