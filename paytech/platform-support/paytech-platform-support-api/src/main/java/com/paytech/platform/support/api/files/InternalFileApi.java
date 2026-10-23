package com.paytech.platform.support.api.files;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.files.request.StartFileReplacementRequestBody;
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
 * <li>响应中凡带 fileId 的同时返回 fileAddress（{@link FileAddress}）；业务数据保存地址，接口路径仍按文件 ID 寻址，调用方用
 * {@link FileAddress#parse(String)} 转换。</li>
 * <li>错误：参数 422/400，文件策略不符 422，不存在 404，状态冲突 409，存储、Vault 或路由不可用 503。</li>
 */
@HttpExchange(url = "/internal/files/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface InternalFileApi {

    /** 人工操作人账号请求头。 */
    String OPERATOR_ACCOUNT_HEADER = "X-Operator-Account-Id";

    /**
     * 开始一次上传：按调用方来源系统的写入路由固定存储实例、桶与扫描策略，签发浏览器直传的 PUT 授权
     * <li>每次请求新建上传；HTTP 请求幂等由调用方或 Gateway 处理。</li>
     * <li>路由按 (来源系统, 访问级别) → (*, 访问级别) 选择，不跨级别回落；未声明访问级别按 PRIVATE。</li>
     *
     * @param requestBody 上传原因、文件名、声明类型与大小、可选上传人与访问级别
     * @return 上传 ID、PUT 授权与上传到期时间；HTTP 201
     *         <li>类型或大小不符合路由扫描策略返回 422 FILE_POLICY_REJECTED；没有该级别的可用路由或桶返回 503。</li>
     */
    @PostExchange(url = "/uploads", contentType = MediaType.APPLICATION_JSON_VALUE)
    FileUploadResponseBody startUpload(@Valid @RequestBody StartFileUploadRequestBody requestBody);

    /**
     * 开始替换文件内容：文件 ID、文件地址与公开地址不变，新内容作为新版本单独检查
     * <li>只有原来源系统可以替换；文件须 AVAILABLE、对象 ACTIVE、记录的桶仍可写，且没有未结束的替换（上传未完成或新版本检查中）。</li>
     * <li>新内容冻结原文件扫描策略代码当前的 ACTIVE 修订；PUT 写到原对象名，由桶版本控制保留旧版本。</li>
     * <li>私有文件在新版本检查通过前下载仍得到旧版本；公开文件的公开地址在 PUT 完成后即读到新内容，检查不通过时 Platform 把旧版本恢复为最新。</li>
     * <li>完成仍调用 {@link #completeUpload(String)}。</li>
     *
     * @param fileId 文件 ID，十进制字符串
     * @param requestBody 新内容的文件名、声明类型与大小、可选上传人
     * @return 上传 ID、PUT 授权与上传到期时间；HTTP 201
     *         <li>其他来源或不存在返回 404 FILE_NOT_FOUND；文件不可用或已有未结束的替换返回 409 FILE_STATE_CONFLICT；类型或大小不符合策略返回
     *         422 FILE_POLICY_REJECTED；桶不可写返回 503。</li>
     */
    @PostExchange(url = "/files/{fileId}/replacements", contentType = MediaType.APPLICATION_JSON_VALUE)
    FileUploadResponseBody startReplacement(@PathVariable("fileId") String fileId,
        @Valid @RequestBody StartFileReplacementRequestBody requestBody);

    /**
     * 完成上传：核对实际字节、摘要、类型与唯一版本后登记文件（或替换的新版本）并安排扫描
     * <li>只有开始上传的来源系统可以完成；同一上传已完成时返回原结果，不重复核对存储。</li>
     * <li>替换上传要求存储中比开始时新的版本恰好 1 个：0 个返回 409（未检测到上传）；多于 1 个返回 409 并作废本次替换。</li>
     *
     * @param uploadId 上传 ID，十进制字符串
     * @return 上传 ID、文件 ID、文件地址、登记时间、本次版本号与访问级别
     *         <li>其他来源系统的上传返回 404 FILE_NOT_FOUND；已过期或状态不允许返回 409 FILE_STATE_CONFLICT。</li>
     */
    @PostExchange("/uploads/{uploadId}/completion")
    FileUploadCompletionResponseBody completeUpload(@PathVariable("uploadId") String uploadId);

    /**
     * 按文件 ID 查询元数据
     *
     * @param fileId 文件 ID，十进制字符串
     * @return 文件地址、当前版本的大小、类型、摘要与扫描状态、对象状态、来源系统、上传原因、上传人、登记时间、访问级别、版本号、
     *         公开地址（仅 PUBLIC 且 AVAILABLE）与检查中的替换版本号；不含存储坐标
     *         <li>不存在返回 404 FILE_NOT_FOUND。</li>
     */
    @GetExchange("/files/{fileId}")
    StoredFileResponseBody getFile(@PathVariable("fileId") String fileId);

    /**
     * 签发文件当前版本的短时只读下载地址
     * <li>要求扫描为 AVAILABLE、对象 ACTIVE、记录的实例与桶仍可读；地址最长 60 秒有效。</li>
     * <li>地址签入原文件名（filename 为 ASCII 回退，filename* 为 RFC 5987 UTF-8 编码）与下载方式：请求 INLINE 且扫描 AVAILABLE、
     * 登记 MIME 为 application/pdf、image/png、image/jpeg 时内联打开并签入登记的 Content-Type，否则静默降为 ATTACHMENT。</li>
     * <li>带人工操作人时，签发成功后 Platform 写一条 FILE_DOWNLOAD_AUTHORIZE 审计（操作人、文件、调用服务，不记地址）；审计写入失败
     * 时不返回地址。</li>
     *
     * @param fileId 文件 ID，十进制字符串
     * @param operatorAccountId 可选请求头 X-Operator-Account-Id：人工操作人账号 ID，无前导零的正整数十进制字符串；Platform 不核对真实性
     * @param disposition 可选 Query 参数：{@code FileDisposition} Code（INLINE、ATTACHMENT）；缺省或为空按 ATTACHMENT
     * @return 下载地址、到期时间与实际生效的下载方式
     *         <li>操作人或下载方式不合法返回 400 INVALID_REQUEST；状态不允许返回 409 FILE_STATE_CONFLICT；实例或桶已停用、审计不可用返回 503。</li>
     */
    @PostExchange("/files/{fileId}/download")
    FileDownloadResponseBody authorizeDownload(@PathVariable("fileId") String fileId,
        @RequestHeader(name = OPERATOR_ACCOUNT_HEADER, required = false) String operatorAccountId,
        @RequestParam(name = "disposition", required = false) String disposition);
}
