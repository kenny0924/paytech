package com.paytech.commercial.api.term.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 登记客户对 APPROVED 合同价格版本的签署（价格附件 + 签署记录），版本进入 CLIENT_SIGNED
 * <li>acceptanceReference 是业务唯一身份：同编号同内容重放返回原结果，不同内容拒绝。</li>
 */
@Getter
@Setter
public class RecordTermVersionAcceptanceRequestBody {
    /** 方案 BCR 根当前修订版本（CAS 令牌）；不等于当前值时 409。 */
    @NotBlank(message = "expectedChangeVersion 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "expectedChangeVersion 格式不正确")
    private String expectedChangeVersion;

    /** 签署 Party：Client 方案为合同 CLIENT 角色 Party，Partner 方案为合同 LOCAL_PARTNER 角色 Party */
    @NotBlank(message = "partyId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "partyId 格式不正确")
    private String partyId;

    /** 签署方式，同合同签署：E_SIGNATURE、CLICK_ACCEPTANCE、MANUAL_SIGNATURE */
    @NotBlank(message = "acceptanceMethod 必填")
    @Pattern(regexp = "E_SIGNATURE|CLICK_ACCEPTANCE|MANUAL_SIGNATURE",
        message = "acceptanceMethod 只能为 E_SIGNATURE、CLICK_ACCEPTANCE、MANUAL_SIGNATURE")
    private String acceptanceMethod;

    /** 签署成立时刻，带时区偏移的 ISO 8601；不得晚于登记时刻 */
    @NotBlank(message = "acceptedAt 必填")
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})",
        message = "acceptedAt 必须是带时区偏移的 ISO 8601 时间")
    private String acceptedAt;

    /** 全局唯一的签署编号，1–128 位字母数字及 . _ : - */
    @NotBlank(message = "acceptanceReference 必填")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}", message = "acceptanceReference 只能包含字母、数字及 . _ : -，最长 128 位")
    private String acceptanceReference;

    /** 已签署价格附件的 Platform Support 文件 ID */
    @NotBlank(message = "fileId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "fileId 必须是规范正十进制 ID")
    private String fileId;

    /** 价格附件 SHA-256，64 位小写十六进制 */
    @NotBlank(message = "contentHash 必填")
    @Pattern(regexp = "[0-9a-f]{64}", message = "contentHash 必须是 64 位小写十六进制 SHA-256")
    private String contentHash;
}
