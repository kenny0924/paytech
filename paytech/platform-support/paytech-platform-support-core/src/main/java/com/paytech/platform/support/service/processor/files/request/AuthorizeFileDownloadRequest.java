package com.paytech.platform.support.service.processor.files.request;

import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import lombok.Getter;

/** 签发文件下载授权的内部请求；内部系统之间不做文件级权限。 */
@Getter
public class AuthorizeFileDownloadRequest {

    /** Platform 内部模块调用时没有 HTTP 请求 ID。 */
    private static final String UNKNOWN_REQUEST = "UNKNOWN";

    /** 调用方服务名，取内部调用身份；Platform 内部模块为 platform-support。 */
    @NotBlank(message = "调用方服务名必填")
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
     * Platform 内部模块的进程内入口：来源 platform-support，无人工操作人
     *
     * @param fileId 文件 ID
     * @param disposition 下载方式，可空按 ATTACHMENT
     */
    public AuthorizeFileDownloadRequest(long fileId, FileDisposition disposition) {
        this(FileSourceCodes.PLATFORM_SUPPORT, fileId, disposition, null, UNKNOWN_REQUEST);
    }

    /**
     * 完整请求
     *
     * @param callerService 调用方服务名
     * @param fileId 文件 ID
     * @param disposition 下载方式，可空按 ATTACHMENT
     * @param operatorAccountId 人工操作人账号 ID，可空
     * @param requestId 请求 ID
     */
    public AuthorizeFileDownloadRequest(String callerService, long fileId, FileDisposition disposition,
        Long operatorAccountId, String requestId) {
        this.callerService = callerService;
        this.fileId = fileId;
        this.disposition = disposition;
        this.operatorAccountId = operatorAccountId;
        this.requestId = requestId;
    }
}
