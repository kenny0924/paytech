package com.paytech.tax.integration.file.impl;

import java.net.URI;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadAuthorization;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.tax.core.constant.TaxCodes;
import com.paytech.tax.integration.file.TaxDocumentFileClient;

/**
 * 经 Platform 内部文件接口上传税票（technique 9.3、S-12）
 * <li>开始上传与完成走 platform-support-service 服务组（Foundation 公共出站边界与内部调用身份）；内容按签发的方法、地址与 Header 直传对象存储，
 * 使用专用的不重试、不跟随重定向客户端，地址与内容不进入日志。</li>
 * <li>Platform 按（tax-service, PRIVATE）写路由选择存储；路由缺失时 Platform 返回 503，由补偿任务重试。</li>
 */
@Component
public class PlatformTaxDocumentFileClient implements TaxDocumentFileClient {
    /** 访问级别：私有。 */
    static final String ACCESS_PRIVATE = "PRIVATE";

    private final InternalFileApi internalFileApi;

    private final RestClient fileUploadRestClient;

    /**
     * 注入文件接口代理与直传客户端
     *
     * @param internalFileApi Platform 内部文件接口代理
     * @param fileUploadRestClient 对象存储直传客户端
     */
    public PlatformTaxDocumentFileClient(InternalFileApi internalFileApi,
        @Qualifier("taxFileUploadRestClient") RestClient fileUploadRestClient) {
        this.internalFileApi = internalFileApi;
        this.fileUploadRestClient = fileUploadRestClient;
    }

    /** 开始上传 → 直传 → 完成；任一步失败抛出，响应缺字段为系统异常。 */
    @Override
    public String uploadPrivate(String filename, String mimeType, byte[] content) {
        FileUploadResponseBody upload = internalFileApi.startUpload(new StartFileUploadRequestBody(
            TaxCodes.FILE_REASON_TAX_RECEIPT, filename, mimeType, Integer.toString(content.length), null, ACCESS_PRIVATE));
        if (upload == null || upload.getUploadId() == null || upload.getAuthorization() == null) {
            throw new ErrorException("Platform 开始上传响应无效: " + filename);
        }
        put(upload.getAuthorization(), content);
        FileUploadCompletionResponseBody completion = internalFileApi.completeUpload(upload.getUploadId());
        if (completion == null || completion.getFileAddress() == null) {
            throw new ErrorException("Platform 完成上传响应无效: " + upload.getUploadId());
        }
        return completion.getFileAddress();
    }

    /** 按签发的方法、地址与 Header 直传内容；非 2xx 由 RestClient 抛出。 */
    void put(FileUploadAuthorization authorization, byte[] content) {
        Map<String, String> headers = authorization.getHeaders() == null ? Map.of() : authorization.getHeaders();
        fileUploadRestClient.method(HttpMethod.valueOf(authorization.getMethod())).uri(URI.create(authorization.getUrl()))
            .headers(target -> headers.forEach(target::set)).body(content).retrieve().toBodilessEntity();
    }
}
