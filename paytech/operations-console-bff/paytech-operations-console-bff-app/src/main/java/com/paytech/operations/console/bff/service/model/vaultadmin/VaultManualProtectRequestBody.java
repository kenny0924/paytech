package com.paytech.operations.console.bff.service.model.vaultadmin;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

/**
 * 运营人工加密；页面不传数据级别，由 BFF 按路由决定。
 */
@Getter
@Setter
public class VaultManualProtectRequestBody {
    /**
     * 页面提交 ID
     * <li>每次打开表单生成；重复提交返回同一 Token。</li>
     * <li>作为 Vault 的 Idempotency-Key，不使用 Gateway 的 HTTP 幂等。</li>
     */
    @NotNull(message = "提交 ID 必填")
    @Pattern(regexp = "[A-Za-z0-9._~-]{1,128}", message = "提交 ID 格式无效")
    private String submissionId;

    /**
     * 数据类型编码
     * <li>大写字母开头，1..64 位大写字母、数字或下划线。</li>
     */
    @NotNull(message = "数据类型编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "数据类型编码格式无效")
    private String dataTypeCode;

    /**
     * 待加密的原值
     * <li>不超过 32 KiB；不记录日志。</li>
     */
    @NotNull(message = "待加密的值必填")
    private String value;

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
