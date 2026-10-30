package com.paytech.payout.api.beneficiary.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交新收款账户请求（P1）
 * <li>含原值字段：不生成 toString；服务把 /payout/v1/beneficiary-accounts 前缀配置为 MVC 日志 metadata-only。</li>
 * <li>Client BFF 提交时 ownerType 固定为 CLIENT、ownerId 取当前工作区，actorType 为 CLIENT_MEMBER；Local Partner 只能由运营代提交。</li>
 */
@Getter
@Setter
public class SubmitBeneficiaryAccountRequestBody {
    /**
     * Owner 类型
     * <li>CLIENT 或 LOCAL_PARTNER。</li>
     */
    @NotBlank
    @Pattern(regexp = "CLIENT|LOCAL_PARTNER", message = "只能为 CLIENT 或 LOCAL_PARTNER")
    private String ownerType;

    /**
     * Owner 标识
     * <li>client-master 的 Client 或 Local Partner 正整数 ID 字符串。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须为规范正整数字符串")
    private String ownerId;

    /**
     * 收款方式
     * <li>LOCAL 或 SWIFT。</li>
     */
    @NotBlank
    @Pattern(regexp = "LOCAL|SWIFT", message = "只能为 LOCAL 或 SWIFT")
    private String railType;

    /**
     * 开户国家
     * <li>大写 ISO 3166-1 alpha-2。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}", message = "必须是大写 ISO 3166-1 两位代码")
    private String bankCountry;

    /**
     * 收款币种
     * <li>大写 ISO 4217。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /** 账户字段（含原值） */
    @NotNull
    @Valid
    private BeneficiaryAccountFields fields;

    /**
     * 提交人类型
     * <li>CLIENT_MEMBER（Client 门户）或 OPERATIONS（运营代提交）。</li>
     */
    @NotBlank
    @Pattern(regexp = "CLIENT_MEMBER|OPERATIONS", message = "只能为 CLIENT_MEMBER 或 OPERATIONS")
    private String actorType;

    /**
     * 提交人 Platform 账号 ID
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须为规范正整数字符串")
    private String actorId;
}
