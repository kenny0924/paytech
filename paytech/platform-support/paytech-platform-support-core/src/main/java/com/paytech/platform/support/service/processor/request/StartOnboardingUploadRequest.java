package com.paytech.platform.support.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 开始入驻文件上传的内部请求；身份与数值已由入口按规范正整数解析，文件名保持浏览器原值
 * <li>字段约束与 StartUploadRequestBody 等价，由公共 validateBasic 执行，失败返回 400 INVALID_REQUEST。</li>
 */
@Getter
@AllArgsConstructor
public class StartOnboardingUploadRequest {

    /**
     * 当前账号 ID
     * <li>来自 Gateway 已验证的 Actor Subject，与会话一起在事务内重新核验。</li>
     */
    @Positive(message = "账号 ID 必须为正整数")
    private final long accountId;

    /** 当前 CLIENT 会话 ID。 */
    @Positive(message = "会话 ID 必须为正整数")
    private final long sessionId;

    /** CCT 原申请 ID。 */
    @Positive(message = "申请 ID 必须为正整数")
    private final long applicationId;

    /** 申请下的材料主体 Party ID。 */
    @Positive(message = "主体 ID 必须为正整数")
    private final long subjectPartyId;

    /**
     * 浏览器提交的原始文件名
     * <li>展示名由 {@link #displayName()} 归一化得到，不直接落库。</li>
     */
    @NotBlank(message = "文件名必填")
    private final String filename;

    /**
     * 声明的规范 MIME
     * <li>小写 type/subtype；实际字节在完成时独立验证。</li>
     */
    @NotNull(message = "mimeType 必填")
    @Size(max = 128, message = "mimeType 最多 128 个字符")
    @Pattern(regexp = "[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*", message = "mimeType 必须为规范媒体类型")
    private final String mimeType;

    /** 声明字节数。 */
    @Positive(message = "字节数必须为正整数")
    private final long sizeBytes;

    /**
     * 归一化展示名
     * <li>反斜杠视为路径分隔符，只保留最后一段；清除控制与格式字符后去除首尾空白。</li>
     */
    public String displayName() {
        String name = filename.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
    }

    /** 不记录可能包含个人信息的文件名。 */
    @Override
    public String toString() {
        return "StartOnboardingUploadRequest[REDACTED]";
    }
}
