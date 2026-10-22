package com.paytech.client.console.bff.integration;

import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

/**
 * Platform 内部文件接口的出站端口；来源系统由调用身份 client-console-bff 决定
 * <li>Platform 不做文件级业务授权，浏览器侧的授权由调用方 Processor 完成。</li>
 * <li>回执与请求的身份不一致、状态码未发布或必需字段缺失都按依赖不可用抛出。</li>
 */
public interface PlatformFileClient {

    /**
     * 开始一次上传，取得浏览器直传的 PUT 授权。
     *
     * @param request 上传原因、文件名、声明类型与大小及上传人
     * @return 上传 ID 与 PUT 授权
     */
    FileUploadResponseBody startUpload(StartFileUploadRequestBody request);

    /**
     * 完成原上传并登记文件。
     *
     * @param uploadId 上传 ID
     * @return 上传 ID 一致、文件地址与文件 ID 对应的完成回执
     */
    FileUploadCompletionResponseBody completeUpload(String uploadId);

    /**
     * 查询文件元数据。
     *
     * @param fileId 文件 ID
     * @return 文件 ID 与地址一致、扫描与对象状态已发布的元数据；不存在时 Platform 返回 FILE_NOT_FOUND
     */
    StoredFileResponseBody file(String fileId);

    /**
     * 签发原版本的短时下载地址；不带人工操作人，客户下载不在 Platform 审计。
     *
     * @param fileId 文件 ID
     * @return 地址与到期时间
     */
    FileDownloadResponseBody authorizeDownload(String fileId);
}
