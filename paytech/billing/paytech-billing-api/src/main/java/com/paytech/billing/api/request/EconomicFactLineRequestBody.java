package com.paytech.billing.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 经济事实明细行：费用类型、付方、收方、金额与方向。 */
@Getter
@Setter
public class EconomicFactLineRequestBody {
    /**
     * 费用类型代码
     * <li>沿用 AmountSource 代码（GROSS_AMOUNT 除外）；COST_ADJUSTMENT 只允许 CHANNEL_COST。</li>
     * <li>PARTNER_SHARE、PLATFORM_SERVICE_CHARGE 要求头部合作方三元组存在；平台服务费的收方必须是头部 Finance Entity。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
    private String feeTypeCode;

    /**
     * 付方角色
     * <li>CLIENT、LOCAL_PARTNER、CHANNEL、OPERATING_ENTITY、FINANCE_ENTITY、TAX_AUTHORITY、CONSUMER、PLATFORM。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String payerRole;

    /**
     * 付方标识
     * <li>OPERATING_ENTITY 为 entityNo，其余为正十进制整数字符串；CLIENT、LOCAL_PARTNER、FINANCE_ENTITY、OPERATING_ENTITY
     * 必须分别等于头部 clientId、localPartnerId、financeEntityId、entityNo。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{1,19}", message = "必须是 entityNo 或正十进制整数字符串")
    private String payerId;

    /** 收方角色，取值同 payerRole；付方与收方不得相同 */
    @NotBlank
    @Size(max = 64)
    private String payeeRole;

    /** 收方标识，格式与一致性规则同 payerId */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{1,19}", message = "必须是 entityNo 或正十进制整数字符串")
    private String payeeId;

    /**
     * 金额
     * <li>币种主单位的正十进制字符串，精度不超过币种 minor unit，不隐式舍入。</li>
     */
    @NotBlank
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的普通十进制字符串")
    private String amount;

    /**
     * 影响方向
     * <li>CHARGE、REVERSAL、ADJUSTMENT。</li>
     */
    @NotBlank
    @Pattern(regexp = "CHARGE|REVERSAL|ADJUSTMENT", message = "只能为 CHARGE、REVERSAL 或 ADJUSTMENT")
    private String direction;
}
