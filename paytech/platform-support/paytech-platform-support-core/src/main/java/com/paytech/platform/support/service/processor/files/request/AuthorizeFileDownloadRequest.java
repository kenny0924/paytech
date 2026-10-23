package com.paytech.platform.support.service.processor.files.request;

import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;

/** 签发文件下载授权的内部请求；内部系统之间不做文件级权限。 */
@Getter
public class AuthorizeFileDownloadRequest {

    /** Platform 内部模块调用时没有 HTTP 请求 ID。 */
    private static final String UNKNOWN_REQUEST = "UNKNOWN";

    /**
     * 调用方服务名
     * <li>内部 HTTP 取调用身份，Platform 内部模块为 platform-support；运营文件记录下载为空（审计不记 caller_service）。</li>
     */
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "调用方服务名必须为服务名")
    private final String callerService;

    /** 文件 ID。 */
    @Positive(message = "文件 ID 必须为正整数")
    private final long fileId;

    /**
     * 请求的下载方式
     * <li>可空，按 ATTACHMENT；INLINE 不满足条件时静默降为 ATTACHMENT。</li>
     */
    private final FileDisposition disposition;

    /**
     * 人工操作人账号 ID
     * <li>可空；有值时签发成功后写一条下载审计。Platform 不核对真实性，原样记录。</li>
     */
    @Positive(message = "操作人账号 ID 必须为正整数")
    private final Long operatorAccountId;

    /** 请求 ID，写入审计；无请求上下文时为 UNKNOWN。 */
    @NotBlank(message = "请求 ID 必填")
    private final String requestId;

    /**
     * 下载的版本号
     * <li>可空，取当前版本；指定时该版本须为 CURRENT 或 SUPERSEDED 且检查 AVAILABLE，审计记录 version_no。</li>
     */
    @Positive(message = "版本号必须为正整数")
    private final Integer versionNo;

    /**
     * Platform 内部模块的进程内入口：来源 platform-support，无人工操作人
     *
     * @param fileId 文件 ID
     * @param disposition 下载方式，可空按 ATTACHMENT
     */
    public AuthorizeFileDownloadRequest(long fileId, FileDisposition disposition) {
        this(FileSourceCodes.PLATFORM_SUPPORT, fileId, disposition, null, UNKNOWN_REQUEST, null);
    }

    /**
     * 完整请求
     *
     * @param callerService 调用方服务名
     * @param fileId 文件 ID
     * @param disposition 下载方式，可空按 ATTACHMENT
     * @param operatorAccountId 人工操作人账号 ID，可空
     * @param requestId 请求 ID
     * @param versionNo 下载的版本号，可空取当前版本
     */
    public AuthorizeFileDownloadRequest(String callerService, long fileId, FileDisposition disposition,
        Long operatorAccountId, String requestId, Integer versionNo) {
        this.callerService = callerService;
        this.fileId = fileId;
        this.disposition = disposition;
        this.operatorAccountId = operatorAccountId;
        this.requestId = requestId;
        this.versionNo = versionNo;
    }
}
