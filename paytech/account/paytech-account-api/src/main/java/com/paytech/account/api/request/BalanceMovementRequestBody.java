package com.paytech.account.api.request;

import com.paytech.foundation.money.Money;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 余额桶移动请求。 */
@Getter
@Setter
public class BalanceMovementRequestBody {
    /**
     * 移动来源类型
     * <li>PAYOUT_LOCK、TAX_PAYMENT_LOCK 只允许 AVAILABLE → LOCKED；PAYOUT_RELEASE、TAX_PAYMENT_RELEASE 只允许 LOCKED → AVAILABLE。</li>
     * <li>FACT_MATURITY 仅 account 内部使用，不接受。</li>
     */
    @NotBlank
    @Pattern(regexp = "PAYOUT_LOCK|PAYOUT_RELEASE|TAX_PAYMENT_LOCK|TAX_PAYMENT_RELEASE", message = "移动来源类型非法")
    private String sourceType;

    /**
     * 来源业务的稳定引用（如出款单号）
     * <li>1–128 个可见 ASCII 字符；与 sourceType 一起构成幂等身份。</li>
     */
    @NotBlank
    @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "来源引用必须是 1–128 个可见 ASCII 字符")
    private String sourceRef;

    /** 主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
    @NotBlank
    @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY", message = "主体类型非法")
    private String entityType;

    /**
     * 主体标识
     * <li>OPERATING_ENTITY 为 entityNo（如 AE001），FINANCE_ENTITY 为正十进制主键；与主体类型的对应关系由服务端校验。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{1,19}", message = "主体标识非法")
    private String entityId;

    /** 大写 ISO 4217 币种，必须与 amount 币种一致。 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "币种非法")
    private String currency;

    /**
     * 科目代码
     * <li>必须是分桶的 Owner 账户类型（CLIENT_PAYABLE、PARTNER_PAYABLE、BANK_CASH）且与 ownerType 匹配，否则 MOVEMENT_NOT_ALLOWED。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "科目代码非法")
    private String accountCode;

    /** 核算对象类型：CLIENT、LOCAL_PARTNER 或 CASH_ACCOUNT。 */
    @NotBlank
    @Pattern(regexp = "CLIENT|LOCAL_PARTNER|CASH_ACCOUNT", message = "核算对象类型非法")
    private String ownerType;

    /** 核算对象规范十进制标识。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "核算对象标识必须是正十进制整数字符串")
    private String ownerId;

    /** 转出余额桶：必须等于来源类型的固定转出桶。 */
    @NotBlank
    @Pattern(regexp = "PENDING|AVAILABLE|LOCKED", message = "转出余额桶非法")
    private String fromBucket;

    /** 转入余额桶：必须等于来源类型的固定转入桶。 */
    @NotBlank
    @Pattern(regexp = "PENDING|AVAILABLE|LOCKED", message = "转入余额桶非法")
    private String toBucket;

    /**
     * 移动金额
     * <li>正数，主货币单位十进制字符串，不超过币种精度；币种必须等于 currency。</li>
     */
    @NotNull
    private Money amount;

    /**
     * 发起移动的内部操作人，规范十进制标识
     * <li>可空：系统调用方不传；只用于审计。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "操作人标识必须是正十进制整数字符串")
    private String operatorId;
}
