package com.paytech.billing.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 计费事实明细行：组件类型、付方、收方、金额、方向与可选目标余额桶。 */
@Getter
@Setter
public class BillingFactLineRequestBody {
    /**
     * 金额组成类型代码（FactComponentType）
     * <li>MANUAL_ADJUSTMENT 允许 CLIENT_PRINCIPAL、CLIENT_FEE、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、SELLER_TAX、CHANNEL_COST；
     * COST_ADJUSTMENT 只允许 CHANNEL_COST。</li>
     * <li>PARTNER_SHARE、PLATFORM_SERVICE_CHARGE 要求头部合作方二元组存在；平台服务费的收方必须是头部 Finance Entity。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
    private String componentType;

    /**
     * 付方角色（FactPartyRole）
     * <li>CLIENT、LOCAL_PARTNER、CHANNEL、OPERATING_ENTITY、FINANCE_ENTITY、TAX_AUTHORITY、CASH_ACCOUNT、FX_ACCOUNT、CONSUMER；
     * COST_ADJUSTMENT 的付方 / 收方只能是 CHANNEL 与 OPERATING_ENTITY。</li>
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
     * <li>CHARGE、REVERSAL、ADJUSTMENT；COST_ADJUSTMENT 只允许 ADJUSTMENT。</li>
     */
    @NotBlank
    @Pattern(regexp = "CHARGE|REVERSAL|ADJUSTMENT", message = "只能为 CHARGE、REVERSAL 或 ADJUSTMENT")
    private String direction;

    /**
     * 收方 Owner 账户的目标余额桶
     * <li>可为空，为空按 AVAILABLE；仅 MANUAL_ADJUSTMENT 且收方为 CLIENT 或 LOCAL_PARTNER 时允许非空。</li>
     * <li>本期只校验、落库并进入事件载荷，account 继续按现行映射记账。</li>
     */
    @Pattern(regexp = "PENDING|AVAILABLE", message = "只能为 PENDING 或 AVAILABLE")
    private String targetBucket;
}
