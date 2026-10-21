package com.paytech.billing.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 计费事实引用的外部业务对象。 */
@Getter
@Setter
public class BillingFactRefRequestBody {
    /**
     * 引用类型代码（FactRefType）
     * <li>MANUAL_ADJUSTMENT 只允许可选 TICKET；COST_ADJUSTMENT 必须有 CLEARING_ACCOUNT。同一事实内每种类型至多一条。CHANNEL_TRANSACTION_REF、
     * RECON_RESOLUTION 只由自动事实登记，人工登记一律拒绝（400 INVALID_REQUEST）。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z_]{0,31}", message = "必须是大写字母开头的大写字母或下划线，最长 32 位")
    private String refType;

    /**
     * 按 refType 解释的稳定标识
     * <li>数字 ID 使用规范十进制字符串（如 CLEARING_ACCOUNT 为 reconciliation 清算账户主键）；TICKET 为工单号。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", message = "必须是字母或数字开头的安全 ASCII 文本，最长 64 位")
    private String refId;
}
