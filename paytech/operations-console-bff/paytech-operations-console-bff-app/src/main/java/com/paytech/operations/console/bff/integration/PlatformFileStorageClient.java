package com.paytech.operations.console.bff.integration;

import java.time.Instant;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.filestorage.request.ActivateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.ChangeStorageStatusRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DeleteStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DiscardScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.RegisterStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.ReplaceStorageCredentialRequestBody;
import com.paytech.platform.support.api.filestorage.request.RetireScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.response.FileRecordDetailResponseBody;
import com.paytech.platform.support.api.filestorage.response.FileRecordSummary;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyDetailResponseBody;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageBucketCheckResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageRouteListResponseBody;

/**
 * Platform 存储管理的本地端口；实例、桶、写入路由、检查策略与文件记录的规则、FILE_STORAGE_* / FILE_RECORD_* 再次鉴权与审计归 Platform
 * <li>页面请求与响应直接使用 Platform 管理合同的模型，BFF 不增加页面字段；每次调用都携带会话 Account ID 与 Session ID。</li>
 * <li>新建实例与更换密钥的请求含存储密钥，BFF 只转发、不记录、不回显。</li>
 *
 * @author Claude
 */
public interface PlatformFileStorageClient {

    /**
     * 存储实例列表，含桶与被路由引用数；只返回是否已配置密钥
     *
     * @param context 操作人
     * @return 全部实例
     */
    StorageInstanceListResponseBody listInstances(OperatorContext context);

    /**
     * 新建 DRAFT 存储实例
     *
     * @param context 操作人
     * @param request 页面请求，含可选密钥
     * @return 保存回执，objectCode 为实例代码
     */
    ConfigurationMutationResponseBody createInstance(OperatorContext context, CreateStorageInstanceRequestBody request);

    /**
     * 按原版本修改实例连接设置
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody updateInstance(OperatorContext context, String instanceCode,
        UpdateStorageInstanceRequestBody request);

    /**
     * 按原版本更换密钥或切换凭据方式
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param request 页面请求，含新密钥
     * @return 保存回执
     */
    ConfigurationMutationResponseBody replaceCredential(OperatorContext context, String instanceCode,
        ReplaceStorageCredentialRequestBody request);

    /**
     * 按原版本切换实例状态
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody changeInstanceStatus(OperatorContext context, String instanceCode,
        ChangeStorageStatusRequestBody request);

    /**
     * 在实例下登记 DRAFT 桶
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param request 页面请求
     * @return 保存回执，objectCode 为 {实例代码}/{桶名}
     */
    ConfigurationMutationResponseBody registerBucket(OperatorContext context, String instanceCode,
        RegisterStorageBucketRequestBody request);

    /**
     * 按原版本修改 DRAFT 公开桶的对外基础地址
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param bucketName 桶名
     * @param request 页面请求
     * @return 保存回执，objectCode 为 {实例代码}/{桶名}
     */
    ConfigurationMutationResponseBody updateBucket(OperatorContext context, String instanceCode, String bucketName,
        UpdateStorageBucketRequestBody request);

    /**
     * 检查桶并由 Platform 回写结果
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param bucketName 桶名
     * @return 检查结果与时间
     */
    StorageBucketCheckResponseBody checkBucket(OperatorContext context, String instanceCode, String bucketName);

    /**
     * 按原版本切换桶状态
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param bucketName 桶名
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody changeBucketStatus(OperatorContext context, String instanceCode, String bucketName,
        ChangeStorageStatusRequestBody request);

    /**
     * 写入路由列表
     *
     * @param context 操作人
     * @return 全部路由
     */
    StorageRouteListResponseBody listRoutes(OperatorContext context);

    /**
     * 新建写入路由
     *
     * @param context 操作人
     * @param request 页面请求
     * @return 保存回执，objectCode 为 {来源系统}/{访问级别}
     */
    ConfigurationMutationResponseBody createRoute(OperatorContext context, CreateStorageRouteRequestBody request);

    /**
     * 按原版本修改写入路由
     *
     * @param context 操作人
     * @param sourceSystem 来源系统，默认路由为 *
     * @param accessLevel 访问级别
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody updateRoute(OperatorContext context, String sourceSystem, String accessLevel,
        UpdateStorageRouteRequestBody request);

    /**
     * 按原版本删除写入路由；默认私有路由由 Platform 拒绝
     *
     * @param context 操作人
     * @param sourceSystem 来源系统
     * @param accessLevel 访问级别
     * @param request 页面请求
     * @return 删除前的身份与版本
     */
    ConfigurationMutationResponseBody deleteRoute(OperatorContext context, String sourceSystem, String accessLevel,
        DeleteStorageRouteRequestBody request);

    /**
     * 检查策略列表
     *
     * @param context 操作人
     * @return 每个策略代码一行与格式检查器能识别的类型
     */
    ScanPolicyListResponseBody listScanPolicies(OperatorContext context);

    /**
     * 检查策略详情
     *
     * @param context 操作人
     * @param policyCode 策略代码
     * @return 引用的写入路由与全部修订
     */
    ScanPolicyDetailResponseBody getScanPolicy(OperatorContext context, String policyCode);

    /**
     * 新建检查策略及其草稿修订 1
     *
     * @param context 操作人
     * @param request 页面请求
     * @return 保存回执，objectCode 为 {策略代码}/{修订号}
     */
    ConfigurationMutationResponseBody createScanPolicy(OperatorContext context, CreateScanPolicyRequestBody request);

    /**
     * 以生效修订为模板新建草稿修订
     *
     * @param context 操作人
     * @param policyCode 策略代码
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody createScanPolicyRevision(OperatorContext context, String policyCode,
        CreateScanPolicyRevisionRequestBody request);

    /**
     * 按原版本修改草稿修订
     *
     * @param context 操作人
     * @param policyCode 策略代码
     * @param revisionNo 修订号
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody updateScanPolicyRevision(OperatorContext context, String policyCode, int revisionNo,
        UpdateScanPolicyRevisionRequestBody request);

    /**
     * 按原版本废弃草稿修订
     *
     * @param context 操作人
     * @param policyCode 策略代码
     * @param revisionNo 修订号
     * @param request 页面请求
     * @return 删除前的身份与版本
     */
    ConfigurationMutationResponseBody discardScanPolicyRevision(OperatorContext context, String policyCode, int revisionNo,
        DiscardScanPolicyRevisionRequestBody request);

    /**
     * 按原版本激活草稿修订
     *
     * @param context 操作人
     * @param policyCode 策略代码
     * @param revisionNo 修订号
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody activateScanPolicyRevision(OperatorContext context, String policyCode,
        int revisionNo, ActivateScanPolicyRevisionRequestBody request);

    /**
     * 按原版本停用生效修订
     *
     * @param context 操作人
     * @param policyCode 策略代码
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody retireScanPolicy(OperatorContext context, String policyCode,
        RetireScanPolicyRequestBody request);

    /**
     * 文件记录分页
     *
     * @param context 操作人
     * @param query 浏览器查询条件，原样转发
     * @return 分页结果
     */
    PagingResponseBody<FileRecordSummary> listFileRecords(OperatorContext context, FileRecordQuery query);

    /**
     * 文件记录详情
     *
     * @param context 操作人
     * @param fileId 文件 ID
     * @return 元数据、版本历史、检查结果与上传记录
     */
    FileRecordDetailResponseBody getFileRecord(OperatorContext context, String fileId);

    /**
     * 签发某一版本的短时下载地址；Platform 写下载审计
     *
     * @param context 操作人
     * @param fileId 文件 ID
     * @param versionNo 可选版本号，缺省为当前版本
     * @return 下载地址与到期时间；地址不进入日志
     */
    FileDownloadResponseBody downloadFileRecord(OperatorContext context, String fileId, Integer versionNo);

    /**
     * 文件记录查询条件；全部可选，原样转发 Platform
     *
     * @param createdFrom 创建时间起点（UTC，包含）
     * @param createdTo 创建时间终点（UTC，不包含）
     * @param fileId 文件 ID
     * @param sourceSystem 来源系统
     * @param reasonCode 上传原因代码
     * @param accessLevel 访问级别
     * @param scanStatus 当前版本扫描状态
     * @param hasPendingReplacement 是否有检查中的替换版本
     * @param page 页码
     * @param size 每页条数
     */
    record FileRecordQuery(Instant createdFrom, Instant createdTo, String fileId, String sourceSystem, String reasonCode,
        String accessLevel, String scanStatus, Boolean hasPendingReplacement, Integer page, Integer size) {
    }
}
