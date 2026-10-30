package com.paytech.payout.api.beneficiary.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Checker 复核决定请求（P9）
 * <li>decision 为 REJECTED 时 reasonCode 必填，取 BeneficiaryRejectReason；APPROVED 时为空，且 Maker 意见必须为 APPROVE。</li>
 * <li>clientNote 只在 REJECTED 时可填，随拒绝通知的 reasonText 发给 Owner；未填写时沿用 Maker 建议拒绝时的说明。</li>
 * <li>actorId 等于 Maker 或运营代提交人时 403 FOUR_EYES_REQUIRED；contentHash / expectedVersion 不一致时 409。</li>
 */
@Getter
@Setter
public class CheckerDecisionRequestBody {
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
     * <li>可选，最多 500 字符，只在 REJECTED 时可填（APPROVED 时给出为 400）；空白视为未填写。</li>
     */
    @Size(max = 500)
    private String clientNote;

    /** 审阅的内容摘要（64 位小写十六进制） */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}", message = "必须为 64 位小写十六进制")
    private String contentHash;

    /** 期望的账户版本 */
    @NotBlank
    @Pattern(regexp = "0|[1-9][0-9]{0,18}", message = "必须为非负整数字符串")
    private String expectedVersion;

    /** Checker 的 Operations 账号 ID */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须为规范正整数字符串")
    private String actorId;
}
