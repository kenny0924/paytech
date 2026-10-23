package com.paytech.platform.support.service.processor.files.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.paytech.platform.support.filedocument.domain.file.FileDisplayName;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 开始替换文件内容的内部请求；内部 HTTP 入口与 Platform 内部模块共用
 * <li>来源系统由入口确定，只有原来源系统可以替换；访问级别、上传原因与存储位置沿用原文件。</li>
 */
@Getter
@AllArgsConstructor
public class StartFileReplacementRequest {

    /** 来源系统，即内部调用身份的服务名。 */
    @NotNull(message = "来源系统必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "来源系统必须为服务名")
    private final String sourceSystem;

    /** 被替换内容的文件 ID。 */
    @Positive(message = "文件 ID 必须为正整数")
    private final long fileId;

    /**
     * 新内容的原始文件名
     * <li>展示名由 {@link #displayName()} 归一化得到。</li>
     */
    @NotBlank(message = "文件名必填")
    private final String filename;

    /** 新内容声明的规范 MIME。 */
    @NotNull(message = "mimeType 必填")
    @Size(max = 128, message = "mimeType 最多 128 个字符")
    @Pattern(regexp = "[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*", message = "mimeType 必须为规范媒体类型")
    private final String mimeType;

    /** 新内容声明字节数。 */
    @Positive(message = "字节数必须为正整数")
    private final long sizeBytes;

    /** 上传人账号；可空，Platform 不核对真实性。 */
    @Positive(message = "上传人账号必须为正整数")
    private final Long uploaderAccountId;

    /** 清理后的展示名。 */
    public String displayName() {
        return FileDisplayName.normalize(filename);
    }

    /** 不记录可能包含个人信息的文件名。 */
    @Override
    public String toString() {
        return "StartFileReplacementRequest[" + fileId + "]";
    }
}
