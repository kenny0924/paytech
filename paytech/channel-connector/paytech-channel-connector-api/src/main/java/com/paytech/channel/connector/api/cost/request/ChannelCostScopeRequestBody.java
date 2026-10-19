package com.paytech.channel.connector.api.cost.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本适用范围：六个维度逐值精确匹配。 */
@Getter
@Setter
public class ChannelCostScopeRequestBody {
    /**
     * 版本内范围序号
     * <li>正整数，版本内唯一。</li>
     */
    @NotNull
    private Integer scopeNo;

    /**
     * 渠道商户号
     * <li>本库 t_channel_mid 主键；须存在、归属方案的 Channel 且 entity_no 一致。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String midId;

    /**
     * CCT Operating Entity 编号
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "必须是两位大写字母加三位数字")
    private String entityNo;

    /**
     * 该范围实际采用的 Channel 合同或费率文件稳定引用
     * <li>1—128 位，非空白。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String channelAgreementRef;

    /**
     * ISO 3166-1 alpha-2 国家代码
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}", message = "必须是大写 ISO 3166-1 alpha-2 两位代码")
    private String countryCode;

    /**
     * ISO 4217 币种代码
     * <li>只校验格式；也是成本金额的币种。本库无币种表，金额按币种精度归整由 billing 计算时完成。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * Payment 支付方式目录代码
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
    private String paymentMethodCode;

    /**
     * Payment 支付、退款或争议动作类别
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
    private String actionType;

    /**
     * 范围内完整成本组成集
     * <li>非空；组成代码范围内唯一。</li>
     */
    @NotNull
    @Size(max = 20)
    private List<@Valid ChannelCostComponentRequestBody> components;
}
