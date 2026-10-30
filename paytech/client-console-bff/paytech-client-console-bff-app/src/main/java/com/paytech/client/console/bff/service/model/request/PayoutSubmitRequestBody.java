package com.paytech.client.console.bff.service.model.request;

import com.paytech.payout.api.beneficiary.request.BeneficiaryAccountFields;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Client 门户提交收款账户的浏览器请求
 * <li>Owner 固定为当前工作区 Client，提交人为当前成员账号，浏览器不提交 Owner 或提交人。</li>
 * <li>含户名与账号原值：不生成 toString，接口前缀为 MVC 日志 metadata-only。</li>
 */
@Getter
@Setter
public class PayoutSubmitRequestBody {
    /**
     * 收款方式
     * <li>LOCAL 或 SWIFT。</li>
     */
    @NotBlank
    @Pattern(regexp = "LOCAL|SWIFT", message = "只能为 LOCAL 或 SWIFT")
    private String railType;

    /** 开户国家，大写 ISO 3166-1 alpha-2 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}", message = "必须是大写 ISO 3166-1 两位代码")
    private String bankCountry;

    /** 收款币种，大写 ISO 4217 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * 账户字段（含原值）
     * <li>必填与格式按 payout 字段要求校验，BFF 只做长度约束。</li>
     */
    @NotNull
    @Valid
    private BeneficiaryAccountFields fields;
}
