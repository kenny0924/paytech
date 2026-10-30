package com.paytech.operations.console.bff.service.model.payout.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Checker 复核决定的浏览器请求
 * <li>Checker 取当前 Operations 会话；与 Maker 或运营代提交人同一人时 payout 返回 403 FOUR_EYES_REQUIRED。</li>
 * <li>REJECTED 时 reasonCode 必填（BeneficiaryRejectReason），由 payout 判断。</li>
 */
@Getter
@Setter
public class PayoutCheckerDecisionRequestBody {
    /**
     * 复核决定
     * <li>APPROVED 或 REJECTED。</li>
     */
    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED", message = "只能为 APPROVED 或 REJECTED")
    private String decision;

    /** 拒绝原因代码 */
    @Pattern(regexp = "[A-Z][A-Z_]{0,63}", message = "必须是大写原因代码")
    private String reasonCode;

    /**
     * 给 Client 的说明
     * <li>可选，最多 500 字符，只在拒绝时可填（由 payout 判断）；随拒绝通知邮件发给 Owner。</li>
     */
    @Size(max = 500)
    private String clientNote;

    /** 审阅时看到的内容摘要（64 位小写十六进制） */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}", message = "必须为 64 位小写十六进制")
    private String contentHash;

    /** 期望的账户版本 */
    @NotBlank
    @Pattern(regexp = "0|[1-9][0-9]{0,18}", message = "必须为非负整数字符串")
    private String expectedVersion;
}
