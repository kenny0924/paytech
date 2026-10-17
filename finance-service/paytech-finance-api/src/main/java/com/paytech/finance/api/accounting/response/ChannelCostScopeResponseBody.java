package com.paytech.finance.api.accounting.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本适用范围。 */
@Getter
@Setter
public class ChannelCostScopeResponseBody {
    /** 版本内范围序号。 */
    private Integer scopeNo;

    /** Payment Channel MID 稳定业务 ID。 */
    private String midId;

    /** CCT Operating Entity 编号。 */
    private String entityNo;

    /** Channel 合同或费率文件稳定引用。 */
    private String channelAgreementRef;

    /** ISO 3166-1 alpha-2 国家代码。 */
    private String countryCode;

    /** ISO 4217 币种代码，也是成本金额的币种。 */
    private String currency;

    /** Payment 支付方式目录代码。 */
    private String paymentMethodCode;

    /** Payment 支付、退款或争议动作类别。 */
    private String actionType;

    /** 按 componentCode 升序的成本组成。 */
    private List<ChannelCostComponentResponseBody> components;
}
