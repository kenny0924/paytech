package com.paytech.risk.control.service.processor.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Manual Payout 资格评估的 Owner 输入，字段格式已在 HTTP 入口校验。 */
@Getter
@AllArgsConstructor
public class ManualPayoutEligibilityRequest {
    /** 调用方命令键，同一检查点命令重放复用原值。 */
    @NotBlank
    private final String commandKey;

    /** 出款检查点 Code。 */
    @NotBlank
    private final String checkpointType;

    /** 出款角色类型 Code。 */
    @NotBlank
    private final String roleType;

    /** 出款角色 ID 十进制字符串。 */
    @NotBlank
    private final String roleId;

    /** Operating Entity ID 十进制字符串。 */
    @NotBlank
    private final String operatingEntityId;

    /** 出款总额，币种主单位。 */
    @NotNull
    @Positive
    private final BigDecimal grossAmount;

    /** ISO 4217 币种。 */
    @NotBlank
    private final String currency;

    /** 收款银行账户国家。 */
    @NotBlank
    private final String bankCountry;

    /** 来源事实类型 Code。 */
    @NotBlank
    private final String sourceFactType;

    /** 来源事实 ID 十进制字符串。 */
    @NotBlank
    private final String sourceFactId;

    /** 来源事实版本。 */
    @Positive
    private final int sourceFactVersion;

    /** 调用方计算的标准输入摘要。 */
    @NotBlank
    private final String inputHash;
}
