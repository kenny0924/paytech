package com.paytech.payout.api.beneficiary.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Maker 核验意见请求（P8）
 * <li>decision 为 REJECT 时 reasonCode 必填，取 BeneficiaryRejectReason；APPROVE 时为空。</li>
 * <li>note 为可选内部备注，只给运营查看；clientNote 只在 REJECT 时可填，Checker 驳回后随拒绝通知发给 Owner；空白视为未填写。</li>
 * <li>contentHash 必须等于账户当前 contentHash，expectedVersion 必须等于当前版本，否则 409。</li>
 */
@Getter
@Setter
public class MakerReviewRequestBody {
    /**
     * 核验意见
     * <li>APPROVE 或 REJECT。</li>
     */
    @NotBlank
    @Pattern(regexp = "APPROVE|REJECT", message = "只能为 APPROVE 或 REJECT")
    private String decision;

    /** 拒绝原因代码 */
    @Pattern(regexp = "[A-Z][A-Z_]{0,63}", message = "必须是大写原因代码")
    private String reasonCode;

    /**
     * 核验意见备注
     * <li>可选，最多 500 字符；只在运营视图显示（O2 Maker 意见下方），不发给 Owner。</li>
     */
    @Size(max = 500)
    private String note;

    /**
     * 给 Client 的说明
     * <li>可选，最多 500 字符，只在 REJECT 时可填（APPROVE 时给出为 400）；Checker 驳回且未另填时随拒绝通知发送。</li>
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

    /** Maker 的 Operations 账号 ID */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须为规范正整数字符串")
    private String actorId;
}
