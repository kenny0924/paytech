package com.paytech.operations.console.bff.service.model.vaultadmin;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

/**
 * 运营人工解密；页面不传数据级别，由 BFF 按路由决定。
 */
@Getter
@Setter
public class VaultManualRevealRequestBody {
    /**
     * 待解密的 Token
     * <li>不记录日志。</li>
     */
    @NotNull(message = "Token 必填")
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Token 格式无效")
    private String token;

    /**
     * 理由码
     * <li>CUSTOMER_VERIFICATION、DATA_REPAIR、CHANNEL_CONFIGURATION、INCIDENT_INVESTIGATION 或 OTHER。</li>
     */
    @NotNull(message = "理由码必填")
    @Pattern(regexp = "CUSTOMER_VERIFICATION|DATA_REPAIR|CHANNEL_CONFIGURATION|INCIDENT_INVESTIGATION|OTHER", message = "理由码无效")
    private String reasonCode;

    /**
     * 工单号
     */
    @NotNull(message = "工单号必填")
    @Pattern(regexp = "[A-Za-z0-9._#-]{1,64}", message = "工单号只能包含字母、数字及 . _ # -，最长 64 位")
    private String ticketRef;
}
