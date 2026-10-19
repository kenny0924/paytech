package com.paytech.commercial.api.price.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 在一个已冻结的条款版本内解析价格（stage3 §2.3，technique.md §2.4）
 * <li>一次只解析一个版本；CLIENT_PRICING 等 Client 条款 clientId 必填、localPartnerId 为空，Partner 条款相反。</li>
 * <li>按冻结版本的完整历史内容解析，不改用当前 ACTIVE 版本或画像。</li>
 */
@Getter
@Setter
public class ResolveCommercialPriceRequestBody {
    /** 冻结的条款版本 ID。 */
    @NotBlank(message = "termVersionId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "termVersionId 格式不正确")
    private String termVersionId;

    /** 冻结的准确 OE。 */
    @NotBlank(message = "entityNo 必填")
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "entityNo 格式不正确")
    private String entityNo;

    /** 版本冻结的合同 ID。 */
    @NotBlank(message = "agreementId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "agreementId 格式不正确")
    private String agreementId;

    /** 交易冻结的商业时点（UTC RFC 3339，最多六位小数）；版本必须是该时点方案的适用版本。 */
    @NotBlank(message = "contextAsOfTime 必填")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z", message = "contextAsOfTime 必须为 UTC RFC 3339")
    private String contextAsOfTime;

    /** 条款类型，必须等于版本所属方案的条款类型。 */
    @NotBlank(message = "termType 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "termType 格式不正确")
    private String termType;

    /** 交易币种即费用币种（ISO 4217），价格按币种精确匹配。 */
    @NotBlank(message = "currency 必填")
    @Pattern(regexp = "[A-Z]{3}", message = "currency 格式不正确")
    private String currency;

    /** Client Owner；Client 条款必填。 */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "clientId 格式不正确")
    private String clientId;

    /** Local Partner Owner；Partner 条款必填。 */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "localPartnerId 格式不正确")
    private String localPartnerId;

    /** 支付事实追溯 ID；商务只校验事实内容，不跨库读取 Payment。 */
    @NotBlank(message = "paymentFactId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "paymentFactId 格式不正确")
    private String paymentFactId;

    /** 受控定价事实。 */
    @NotNull(message = "paymentFacts 必填")
    @Valid
    private PricingFactsRequestBody paymentFacts;
}
