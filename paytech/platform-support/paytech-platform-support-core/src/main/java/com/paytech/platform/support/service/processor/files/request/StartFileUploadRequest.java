package com.paytech.platform.support.service.processor.files.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.FileDisplayName;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 开始一次文件上传的内部请求；内部 HTTP 入口与 Platform 内部模块共用
 * <li>来源系统由入口确定：HTTP 取内部调用身份的服务名，Platform 内部模块固定为 platform-support；不接受调用方自报。</li>
 */
@Getter
@AllArgsConstructor
public class StartFileUploadRequest {

    /** 来源系统，即内部调用身份的服务名。 */
    @NotNull(message = "来源系统必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "来源系统必须为服务名")
    private final String sourceSystem;

    /** 来源系统声明的上传原因代码。 */
    @NotNull(message = "reasonCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "reasonCode 必须为大写代码")
    private final String reasonCode;

    /**
     * 调用方提交的原始文件名
     * <li>展示名由 {@link #displayName()} 归一化得到。</li>
     */
    @NotBlank(message = "文件名必填")
    private final String filename;

    /** 声明的规范 MIME。 */
    @NotNull(message = "mimeType 必填")
    @Size(max = 128, message = "mimeType 最多 128 个字符")
    @Pattern(regexp = "[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*", message = "mimeType 必须为规范媒体类型")
    private final String mimeType;

    /** 声明字节数。 */
    @Positive(message = "字节数必须为正整数")
    private final long sizeBytes;

    /** 上传人账号；可空，Platform 不核对真实性。 */
    @Positive(message = "上传人账号必须为正整数")
    private final Long uploaderAccountId;

    /**
     * 调用方声明的访问级别
     * <li>可空，按 PRIVATE；PUBLIC 按来源系统的公开写入路由落到公开桶，不回落到私有路由。</li>
     */
    private final FileAccessLevel accessLevel;

    /**
     * 对象名前缀
     * <li>只有 Platform 进程内模块可以指定（如多语言语言包 {@code i18n/应用/发布号/语言/命名空间/摘要}）；内部 HTTP 入口恒为空。</li>
     * <li>由 / 分隔的段组成，每段以字母或数字开头、只含字母、数字、点、下划线与连字符，最多 210 个字符（对象名总长不超过
     * 255）；对象名仍以随机部分结尾。</li>
     */
    @Size(max = 210, message = "对象名前缀最多 210 个字符")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*(/[A-Za-z0-9][A-Za-z0-9._-]*)*", message = "对象名前缀格式不正确")
    private final String objectKeyPrefix;

    /**
     * 不指定对象名前缀的请求：内部 HTTP 入口与工作流附件使用。
     *
     * @param sourceSystem 来源系统
     * @param reasonCode 上传原因代码
     * @param filename 原始文件名
     * @param mimeType 声明的 MIME
     * @param sizeBytes 声明字节数
     * @param uploaderAccountId 上传人账号，可空
     * @param accessLevel 访问级别，可空
     */
    public StartFileUploadRequest(String sourceSystem, String reasonCode, String filename, String mimeType,
        long sizeBytes, Long uploaderAccountId, FileAccessLevel accessLevel) {
        this(sourceSystem, reasonCode, filename, mimeType, sizeBytes, uploaderAccountId, accessLevel, null);
    }

    /** 访问级别；未声明按 PRIVATE。 */
    public FileAccessLevel effectiveAccessLevel() {
        return accessLevel == null ? FileAccessLevel.PRIVATE : accessLevel;
    }

    /** 清理后的展示名。 */
    public String displayName() {
        return FileDisplayName.normalize(filename);
    }

    /** 不记录可能包含个人信息的文件名。 */
    @Override
    public String toString() {
        return "StartFileUploadRequest[REDACTED]";
    }
}
