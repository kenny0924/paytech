package com.paytech.platform.support.api.notificationmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 录入或更换发送账号的 IAM 密钥对
 * <li>密钥只写入 Vault，任何接口、日志与审计都不返回或记录；toString 不输出密钥</li>
 */
@Getter
@Setter
public class ReplaceNotificationCredentialRequestBody {

    /**
     * Access Key ID
     * <li>16..128 个大写字母或数字。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{16,128}", message = "Access Key ID 格式不正确")
    private String accessKeyId;

    /**
     * Secret Access Key
     * <li>1..128 个可见 ASCII 字符，不含空白。</li>
     */
    @NotBlank
    @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "Secret Access Key 格式不正确")
    private String secretAccessKey;

    /**
     * 调用方看到的当前版本
     * <li>不一致返回 409。</li>
     */
    @Positive
    private int expectedVersion;

    /**
     * 操作原因
     * <li>去除首尾空白后 1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;

    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "ReplaceNotificationCredentialRequestBody(expectedVersion=" + expectedVersion + ")";
    }
}
