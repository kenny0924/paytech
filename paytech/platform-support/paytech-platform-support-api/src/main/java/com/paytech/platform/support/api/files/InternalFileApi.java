package com.paytech.platform.support.api.files;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

import jakarta.validation.Valid;

/**
 * 内部文件接口：私网服务之间上传、完成、查询与下载文件，不经 Gateway 暴露
 * <li>所有请求要求 Foundation internal-auth 已识别的内部调用身份（Authorization Bearer 内部 Token；DUAL 模式下可回退
 * X-Caller-Service）；没有身份返回 401 INTERNAL_TOKEN_INVALID。internal-auth.mode=OFF 的环境下这组接口全部拒绝。</li>
 * <li>来源系统取调用身份的服务名，调用方不能在请求体中指定。</li>
 * <li>内部系统之间不做文件级权限：持有文件 ID 的内部服务都可以查询和下载；浏览器侧的业务授权由调用方完成。</li>
 * <li>错误：参数 422/400，文件策略不符 422，不存在 404，状态冲突 409，存储、Vault 或路由不可用 503。</li>
 */
@HttpExchange(url = "/internal/files/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface InternalFileApi {

    /**
     * 开始一次上传：按调用方来源系统的写入路由固定存储实例、桶与扫描策略，签发浏览器直传的 PUT 授权
     * <li>每次请求新建上传；HTTP 请求幂等由调用方或 Gateway 处理。</li>
     *
     * @param requestBody 上传原因、文件名、声明类型与大小、可选上传人
     * @return 上传 ID、PUT 授权与上传到期时间；HTTP 201
     *         <li>类型或大小不符合路由扫描策略返回 422 FILE_POLICY_REJECTED；没有可用路由或桶返回 503。</li>
     */
    @PostExchange(url = "/uploads", contentType = MediaType.APPLICATION_JSON_VALUE)
    FileUploadResponseBody startUpload(@Valid @RequestBody StartFileUploadRequestBody requestBody);

    /**
     * 完成上传：核对实际字节、摘要、类型与唯一版本后登记文件并安排扫描
     * <li>只有开始上传的来源系统可以完成；同一上传已完成时返回原结果，不重复核对存储。</li>
     *
     * @param uploadId 上传 ID，十进制字符串
     * @return 上传 ID、文件 ID 与登记时间
     *         <li>其他来源系统的上传返回 404 FILE_NOT_FOUND；已过期或状态不允许返回 409 FILE_STATE_CONFLICT。</li>
     */
    @PostExchange("/uploads/{uploadId}/completion")
    FileUploadCompletionResponseBody completeUpload(@PathVariable("uploadId") String uploadId);

    /**
     * 按文件 ID 查询元数据
     *
     * @param fileId 文件 ID，十进制字符串
     * @return 大小、类型、摘要、扫描与对象状态、来源系统、上传原因、上传人与登记时间；不含存储坐标
     *         <li>不存在返回 404 FILE_NOT_FOUND。</li>
     */
    @GetExchange("/files/{fileId}")
    StoredFileResponseBody getFile(@PathVariable("fileId") String fileId);

    /**
     * 签发文件原版本的短时只读下载地址
     * <li>要求扫描为 AVAILABLE、对象 ACTIVE、记录的实例与桶仍可读；地址最长 60 秒有效。</li>
     *
     * @param fileId 文件 ID，十进制字符串
     * @return 下载地址与到期时间
     *         <li>状态不允许返回 409 FILE_STATE_CONFLICT；实例或桶已停用返回 503。</li>
     */
    @PostExchange("/files/{fileId}/download")
    FileDownloadResponseBody authorizeDownload(@PathVariable("fileId") String fileId);
}
