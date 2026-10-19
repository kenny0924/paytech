package com.paytech.commercial.api.agreement.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * 登记一个 Party 在合同中全部角色的最终签署结果。
 * <li>acceptanceReference 是业务唯一身份：同引用同内容返回原结果，不同内容或不同 Party 拒绝。</li>
 */
@Getter
@Setter
public class RecordAgreementAcceptanceRequestBody {
    /**
     * 调用方读取到的合同 BCR Root 版本（changeVersion）
     * <li>本次写入以它做 CAS，提交后版本加一；不一致返回 409。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;

    /** 全局唯一的合同接受事件编号，1–128 位字母数字及 . _ : - */
    @NotBlank(message = "acceptanceReference 必填")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}", message = "acceptanceReference 只能包含字母、数字及 . _ : -，最长 128 位")
    private String acceptanceReference;

    /**
     * 签署方式
     * <li>CLICK_ACCEPTANCE 证据为合同 SIGNING_DOCUMENT 且必须提供 acceptedByActorId；E_SIGNATURE / MANUAL_SIGNATURE 必须提供 executedDocument。</li>
     */
    @NotBlank(message = "acceptanceMethod 必填")
    @Pattern(regexp = "E_SIGNATURE|CLICK_ACCEPTANCE|MANUAL_SIGNATURE", message = "acceptanceMethod 只能为 E_SIGNATURE、CLICK_ACCEPTANCE、MANUAL_SIGNATURE")
    private String acceptanceMethod;

    /**
     * 实际完成接受的外部 Actor ID
     * <li>CLICK_ACCEPTANCE 必填，其他方式可空。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "acceptedByActorId 必须是规范正十进制 ID")
    private String acceptedByActorId;

    /** 接受事实成立时刻，带时区偏移的 ISO 8601；不得晚于登记时刻 */
    @NotBlank(message = "acceptedAt 必填")
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})", message = "acceptedAt 必须是带时区偏移的 ISO 8601 时间")
    private String acceptedAt;

    /**
     * 最终执行文件
     * <li>E_SIGNATURE / MANUAL_SIGNATURE 必填，CLICK_ACCEPTANCE 必须为空；每份合同至多一份，已有时必须是同一文件。</li>
     */
    @Valid
    private AgreementFileEntry executedDocument;
}
