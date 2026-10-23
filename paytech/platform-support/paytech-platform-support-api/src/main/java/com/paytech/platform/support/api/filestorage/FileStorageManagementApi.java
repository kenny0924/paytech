package com.paytech.platform.support.api.filestorage;

import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
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

import jakarta.validation.Valid;

/**
 * Operations 存储管理：存储实例、存储桶、写入路由、检查策略与文件记录；Platform 为唯一 Owner，经 Operations BFF 调用
 * <li>存储配置查询要求 FILE_STORAGE_READ 或 FILE_STORAGE_MANAGE，写入要求 FILE_STORAGE_MANAGE；文件记录查询要求 FILE_RECORD_READ，
 * 下载要求 FILE_RECORD_DOWNLOAD；服务端按 Gateway 传递的可信身份重检。</li>
 * <li>写入直接生效并记 Operations 审计（不含密钥）；请求幂等由 Gateway 负责。</li>
 * <li>错误：字段校验 422、业务拒绝 400、不存在 404、版本冲突 409、Vault 或存储不可用 503。</li>
 * <li>修改在本进程提交后立即生效，其他实例最迟在快照刷新间隔（默认 30 秒）后生效。</li>
 */
@HttpExchange(url = "/storage-admin/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface FileStorageManagementApi {

    /** 可信操作人 Header。 */
    String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /** 可信会话 Header。 */
    String SESSION_HEADER = "X-Gateway-Session-Id";

    /**
     * 存储实例列表
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @return 全部实例，含存储桶与引用路由数；只返回是否已配置密钥
     */
    @GetExchange("/instances")
    StorageInstanceListResponseBody listInstances(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session);

    /**
     * 新建 DRAFT 存储实例
     * <li>STATIC 先把密钥存入 Vault（用途 FILE_STORAGE_SETUP）再写库；MINIO 只允许 STATIC 且必须填写地址。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param requestBody 实例代码、类型、连接设置、凭据与原因
     * @return 保存结果，objectCode 为实例代码，version 为 1
     *         <li>实例代码已存在返回 400；Vault 不可用返回 503 且不写库。</li>
     */
    @PostExchange(url = "/instances", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody createInstance(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @Valid @RequestBody CreateStorageInstanceRequestBody requestBody);

    /**
     * 按原版本修改存储实例的地址、区域、路径风格、证书与超时
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param instanceCode 实例代码
     * @param requestBody 目标连接设置、原版本与原因
     * @return 保存结果；无变化时版本不变
     */
    @PutExchange(url = "/instances/{instanceCode}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody updateInstance(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("instanceCode") String instanceCode,
        @Valid @RequestBody UpdateStorageInstanceRequestBody requestBody);

    /**
     * 按原版本更换密钥或切换凭据方式
     * <li>新密钥先存入 Vault，再以短事务替换 Token；之后的存储操作使用新密钥，旧 Token 不再被引用。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param instanceCode 实例代码
     * @param requestBody 凭据方式、密钥、原版本与原因
     * @return 保存结果
     */
    @PutExchange(url = "/instances/{instanceCode}/credential", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody replaceCredential(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("instanceCode") String instanceCode,
        @Valid @RequestBody ReplaceStorageCredentialRequestBody requestBody);

    /**
     * 按原版本切换存储实例状态
     * <li>只允许 DRAFT→ACTIVE（至少一个存储桶 ACTIVE）、ACTIVE→DISABLED（没有路由指向其存储桶）、DISABLED→ACTIVE。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param instanceCode 实例代码
     * @param requestBody 目标状态、原版本与原因
     * @return 保存结果
     */
    @PatchExchange(url = "/instances/{instanceCode}/status", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody changeInstanceStatus(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("instanceCode") String instanceCode,
        @Valid @RequestBody ChangeStorageStatusRequestBody requestBody);

    /**
     * 在实例下登记 DRAFT 存储桶
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param instanceCode 实例代码
     * @param requestBody 存储桶名称、访问级别、对外基础地址（PUBLIC 必填）与原因
     * @return 保存结果，objectCode 为 {实例代码}/{桶名}
     *         <li>同一实例下同名已登记、PUBLIC 缺少或基础地址不合法、PRIVATE 带基础地址返回 400。</li>
     */
    @PostExchange(url = "/instances/{instanceCode}/buckets", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody registerBucket(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("instanceCode") String instanceCode,
        @Valid @RequestBody RegisterStorageBucketRequestBody requestBody);

    /**
     * 按原版本修改 DRAFT 公开桶的对外基础地址
     * <li>只允许 DRAFT 且 PUBLIC 的桶；修改后清除最近检查结果，需重新检查才能启用。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param instanceCode 实例代码
     * @param bucketName 存储桶名称
     * @param requestBody 对外基础地址、原版本与原因
     * @return 保存结果；无变化时版本不变
     *         <li>桶非 DRAFT 或非 PUBLIC、地址不合法返回 400。</li>
     */
    @PutExchange(url = "/instances/{instanceCode}/buckets/{bucketName}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody updateBucket(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("instanceCode") String instanceCode,
        @PathVariable("bucketName") String bucketName, @Valid @RequestBody UpdateStorageBucketRequestBody requestBody);

    /**
     * 检查存储桶并回写结果
     * <li>事务外依次检查：存在、版本控制已开启、写入探测对象、默认加密已生效、读取、匿名访问、删除；实例为 DRAFT 也可检查。</li>
     * <li>匿名访问：PRIVATE 桶不带凭据读取探测对象必须被拒绝（否则 PUBLIC_ACCESS_ALLOWED）；PUBLIC 桶经对外基础地址匿名读取必须
     * 得到原字节（否则 PUBLIC_READ_FAILED）。</li>
     * <li>探测对象位于 {@code .platform-readiness/} 前缀下，检查后删除；Platform 不修改桶配置。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param instanceCode 实例代码
     * @param bucketName 存储桶名称
     * @return 检查结果、时间与回写后的版本
     *         <li>Vault 不可用返回 503 且不回写；存储侧问题作为结果返回并回写。</li>
     */
    @PostExchange("/instances/{instanceCode}/buckets/{bucketName}/check")
    StorageBucketCheckResponseBody checkBucket(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("instanceCode") String instanceCode,
        @PathVariable("bucketName") String bucketName);

    /**
     * 按原版本切换存储桶状态
     * <li>DRAFT→ACTIVE 要求 10 分钟内检查为 OK；ACTIVE↔READ_ONLY；ACTIVE / READ_ONLY→DISABLED。</li>
     * <li>有路由指向该桶时不能设为 READ_ONLY 或 DISABLED。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param instanceCode 实例代码
     * @param bucketName 存储桶名称
     * @param requestBody 目标状态、原版本与原因
     * @return 保存结果
     */
    @PatchExchange(url = "/instances/{instanceCode}/buckets/{bucketName}/status",
        contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody changeBucketStatus(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("instanceCode") String instanceCode,
        @PathVariable("bucketName") String bucketName, @Valid @RequestBody ChangeStorageStatusRequestBody requestBody);

    /**
     * 写入路由列表
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @return 全部路由，含访问级别、目标实例代码与存储桶名称
     */
    @GetExchange("/routes")
    StorageRouteListResponseBody listRoutes(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session);

    /**
     * 新建写入路由
     * <li>路由键为 (来源系统, 访问级别)；目标实例与存储桶必须为 ACTIVE 且桶的访问级别与路由一致，扫描策略必须有 ACTIVE 修订。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param requestBody 来源系统、访问级别、目标、扫描策略、上传期限与原因
     * @return 保存结果，objectCode 为 {来源系统}/{访问级别}
     */
    @PostExchange(url = "/routes", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody createRoute(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @Valid @RequestBody CreateStorageRouteRequestBody requestBody);

    /**
     * 按原版本修改写入路由；只影响之后的新上传
     * <li>目标桶的访问级别必须与路由一致。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param sourceSystem 来源系统；默认路由为 *（URL 中可写为 * 或 %2A）
     * @param accessLevel 访问级别：PRIVATE 或 PUBLIC
     * @param requestBody 目标、扫描策略、上传期限、原版本与原因
     * @return 保存结果；无变化时版本不变
     */
    @PutExchange(url = "/routes/{sourceSystem}/{accessLevel}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody updateRoute(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("sourceSystem") String sourceSystem,
        @PathVariable("accessLevel") String accessLevel, @Valid @RequestBody UpdateStorageRouteRequestBody requestBody);

    /**
     * 按原版本删除写入路由；默认私有路由 (*, PRIVATE) 不能删除
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param sourceSystem 来源系统
     * @param accessLevel 访问级别：PRIVATE 或 PUBLIC
     * @param requestBody 原版本与原因
     * @return 删除前的身份与版本，status 为空
     */
    @DeleteExchange(url = "/routes/{sourceSystem}/{accessLevel}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody deleteRoute(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("sourceSystem") String sourceSystem,
        @PathVariable("accessLevel") String accessLevel, @Valid @RequestBody DeleteStorageRouteRequestBody requestBody);

    /**
     * 检查策略列表
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @return 每个策略代码一行（内容取 ACTIVE，没有时取 DRAFT）与格式检查器能识别的类型
     */
    @GetExchange("/scan-policies")
    ScanPolicyListResponseBody listScanPolicies(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session);

    /**
     * 检查策略详情
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param policyCode 策略代码
     * @return 引用的写入路由与全部修订（倒序，含检查要求）
     *         <li>不存在返回 404。</li>
     */
    @GetExchange("/scan-policies/{policyCode}")
    ScanPolicyDetailResponseBody getScanPolicy(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("policyCode") String policyCode);

    /**
     * 新建检查策略代码及其修订 1（DRAFT）
     * <li>自动写一条检查要求：PLATFORM_FORMAT / FORMAT / 最低版本 1 / 接受 FORMAT_VALID。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param requestBody 策略代码、允许类型、最大字节数与原因
     * @return 保存结果，objectCode 为 {策略代码}/{修订号}，status 为 DRAFT
     *         <li>代码已存在、类型不受支持或重复、大小越界返回 400。</li>
     */
    @PostExchange(url = "/scan-policies", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody createScanPolicy(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @Valid @RequestBody CreateScanPolicyRequestBody requestBody);

    /**
     * 以当前 ACTIVE 修订为模板新建 DRAFT 修订
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param policyCode 策略代码
     * @param requestBody 原因
     * @return 保存结果，objectCode 为 {策略代码}/{修订号}
     *         <li>不存在返回 404；没有 ACTIVE 修订返回 400；已有 DRAFT 返回 409。</li>
     */
    @PostExchange(url = "/scan-policies/{policyCode}/revisions", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody createScanPolicyRevision(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("policyCode") String policyCode,
        @Valid @RequestBody CreateScanPolicyRevisionRequestBody requestBody);

    /**
     * 按原版本修改 DRAFT 修订
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param policyCode 策略代码
     * @param revisionNo 修订号
     * @param requestBody 允许类型、最大字节数、原版本与原因
     * @return 保存结果；无变化时版本不变
     *         <li>修订不存在返回 404；非 DRAFT、类型不受支持或重复、大小越界返回 400；版本不符返回 409。</li>
     */
    @PutExchange(url = "/scan-policies/{policyCode}/revisions/{revisionNo}",
        contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody updateScanPolicyRevision(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("policyCode") String policyCode,
        @PathVariable("revisionNo") int revisionNo, @Valid @RequestBody UpdateScanPolicyRevisionRequestBody requestBody);

    /**
     * 按原版本废弃 DRAFT 修订（物理删除修订及其检查要求）
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param policyCode 策略代码
     * @param revisionNo 修订号
     * @param requestBody 原版本与原因
     * @return 删除前的身份与版本
     *         <li>修订不存在返回 404；非 DRAFT 返回 400；版本不符返回 409。</li>
     */
    @DeleteExchange(url = "/scan-policies/{policyCode}/revisions/{revisionNo}",
        contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody discardScanPolicyRevision(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("policyCode") String policyCode,
        @PathVariable("revisionNo") int revisionNo, @Valid @RequestBody DiscardScanPolicyRevisionRequestBody requestBody);

    /**
     * 按原版本激活 DRAFT 修订：原 ACTIVE 修订改为 RETIRED，草稿改为 ACTIVE，同一事务完成
     * <li>已开始的上传冻结了原修订，不受影响；新修订只影响之后的上传。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param policyCode 策略代码
     * @param revisionNo 修订号
     * @param requestBody 草稿原版本与原因
     * @return 保存结果，status 为 ACTIVE
     *         <li>修订不存在返回 404；非 DRAFT 返回 400；版本不符返回 409。</li>
     */
    @PostExchange(url = "/scan-policies/{policyCode}/revisions/{revisionNo}/activation",
        contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody activateScanPolicyRevision(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("policyCode") String policyCode,
        @PathVariable("revisionNo") int revisionNo,
        @Valid @RequestBody ActivateScanPolicyRevisionRequestBody requestBody);

    /**
     * 按原版本停用检查策略的当前 ACTIVE 修订，不补新修订
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param policyCode 策略代码
     * @param requestBody ACTIVE 修订原版本与原因
     * @return 保存结果，status 为 RETIRED
     *         <li>不存在返回 404；没有 ACTIVE 修订或有写入路由引用该代码返回 400；版本不符返回 409。</li>
     */
    @PostExchange(url = "/scan-policies/{policyCode}/retirement", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody retireScanPolicy(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("policyCode") String policyCode,
        @Valid @RequestBody RetireScanPolicyRequestBody requestBody);

    /**
     * 分页查询文件记录；按创建时间倒序、文件 ID 倒序
     * <li>创建时间范围左闭右开且必填，跨度不超过 31 天；其他条件精确匹配，为空不限制。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param createdFrom 创建时间起点（UTC，包含）
     * @param createdTo 创建时间终点（UTC，不包含）
     * @param fileId 可选文件 ID，十进制字符串
     * @param sourceSystem 可选来源系统
     * @param reasonCode 可选上传原因代码
     * @param accessLevel 可选访问级别：PRIVATE 或 PUBLIC
     * @param scanStatus 可选当前版本扫描状态 {@code FileScanStatus} Code
     * @param hasPendingReplacement 可选：true 只看有检查中替换版本的文件，false 只看没有的
     * @param page 页码，从 1 开始；为空默认 1
     * @param size 每页条数，允许 10、20、50、100、200；为空默认 10
     * @return 分页结果，空页保留总数
     *         <li>范围缺失、颠倒或超过 31 天、条件格式不合法返回 400。</li>
     */
    @GetExchange("/files")
    PagingResponseBody<FileRecordSummary> listFileRecords(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session,
        @RequestParam(value = "createdFrom", required = false) Instant createdFrom,
        @RequestParam(value = "createdTo", required = false) Instant createdTo,
        @RequestParam(value = "fileId", required = false) String fileId,
        @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
        @RequestParam(value = "reasonCode", required = false) String reasonCode,
        @RequestParam(value = "accessLevel", required = false) String accessLevel,
        @RequestParam(value = "scanStatus", required = false) String scanStatus,
        @RequestParam(value = "hasPendingReplacement", required = false) Boolean hasPendingReplacement,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 文件记录详情
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param fileId 文件 ID，十进制字符串
     * @return 元数据、存储位置（不含凭据）、公开地址、版本历史、检查结果与上传记录
     *         <li>不存在返回 404。</li>
     */
    @GetExchange("/files/{fileId}")
    FileRecordDetailResponseBody getFileRecord(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("fileId") String fileId);

    /**
     * 签发文件某一版本的短时附件下载地址并写下载审计
     * <li>版本须为 CURRENT 或 SUPERSEDED 且检查 AVAILABLE；写 FILE_DOWNLOAD_AUTHORIZE 审计（操作人、文件、version_no，不记地址），
     * 审计写入失败时不返回地址。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param fileId 文件 ID，十进制字符串
     * @param versionNo 可选版本号，正整数；缺省为当前版本
     * @return 下载地址、到期时间与下载方式（ATTACHMENT）
     *         <li>文件或版本不存在返回 404；版本不可下载返回 409；存储或审计不可用返回 503。</li>
     */
    @PostExchange("/files/{fileId}/download")
    FileDownloadResponseBody downloadFileRecord(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("fileId") String fileId,
        @RequestParam(value = "versionNo", required = false) Integer versionNo);
}
