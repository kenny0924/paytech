package com.paytech.card.integration.impl;

import java.io.InputStream;
import java.net.URI;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.paytech.card.domain.binlibrary.BinLibrarySourceFile;
import com.paytech.card.integration.PlatformFileClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

/**
 * 经 Platform 内部文件接口读取 BIN 库导入文件
 * <li>元数据与下载授权走 platform-support 服务组（Foundation 公共出站边界）；下载地址是对象存储的短时地址，用专用的不重试、不跟随重定向客户端
 * 流式读取，地址与文件内容都不进入日志。</li>
 */
@Component
public class PlatformFileClientImpl implements PlatformFileClient {
    private final InternalFileApi internalFileApi;
    private final RestClient fileDownloadRestClient;

    /**
     * 注入文件接口代理与下载客户端
     *
     * @param internalFileApi Platform 内部文件接口代理
     * @param fileDownloadRestClient 对象存储短时地址的下载客户端
     */
    public PlatformFileClientImpl(InternalFileApi internalFileApi,
        @Qualifier("fileDownloadRestClient") RestClient fileDownloadRestClient) {
        this.internalFileApi = internalFileApi;
        this.fileDownloadRestClient = fileDownloadRestClient;
    }

    /** 文件不存在（FILE_NOT_FOUND）返回 null，其他失败原样抛出。 */
    @Override
    public BinLibrarySourceFile getFile(long fileId) {
        StoredFileResponseBody body;
        try {
            body = internalFileApi.getFile(Long.toString(fileId));
        } catch (FailException failed) {
            if (FileErrorCode.FILE_NOT_FOUND.getCode().equals(failed.getCode())) {
                return null;
            }
            throw failed;
        }
        if (body == null) {
            throw new ErrorException("Platform 文件元数据响应为空: " + fileId);
        }
        return new BinLibrarySourceFile(body.getFileAddress(), body.getSourceSystem(), body.getReasonCode(), body.getDisplayName(),
            body.getMimeType(), body.getContentHash(), body.getScanStatus(), body.getObjectStatus());
    }

    /**
     * 签发地址后立即建立连接读取
     * <li>系统读取：不带操作人（Platform 不写人工下载审计），下载方式取 Platform 缺省的附件。</li>
     * <li>非 2xx 抛出 {@link ErrorException}；连接或读取中断由 RestClient 包装为 {@code ResourceAccessException}，调用方均按可重试处理。</li>
     */
    @Override
    public <T> T download(long fileId, ContentReader<T> reader) {
        FileDownloadResponseBody authorization = internalFileApi.authorizeDownload(Long.toString(fileId), null, null);
        if (authorization == null || authorization.getUrl() == null) {
            throw new ErrorException("Platform 文件下载授权响应无效: " + fileId);
        }
        return fileDownloadRestClient.get().uri(URI.create(authorization.getUrl())).exchange((request, response) -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ErrorException("BIN 库文件下载失败: HTTP " + response.getStatusCode().value());
            }
            try (InputStream content = response.getBody()) {
                return reader.read(content);
            }
        });
    }
}
