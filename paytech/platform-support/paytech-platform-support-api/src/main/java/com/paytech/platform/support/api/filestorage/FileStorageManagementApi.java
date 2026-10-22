package com.paytech.platform.support.api.filestorage;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.filestorage.request.ChangeStorageStatusRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DeleteStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.RegisterStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.ReplaceStorageCredentialRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.response.StorageBucketCheckResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageRouteListResponseBody;

import jakarta.validation.Valid;

/**
 * Operations 存储管理：存储实例、存储桶与写入路由；Platform 为唯一 Owner，经 Operations BFF 调用
 * <li>查询要求 FILE_STORAGE_READ 或 FILE_STORAGE_MANAGE，写入要求 FILE_STORAGE_MANAGE；服务端按 Gateway 传递的可信身份重检。</li>
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
     * @param requestBody 存储桶名称与原因
     * @return 保存结果，objectCode 为 {实例代码}/{桶名}
     *         <li>同一实例下同名已登记返回 400。</li>
     */
    @PostExchange(url = "/instances/{instanceCode}/buckets", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody registerBucket(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("instanceCode") String instanceCode,
        @Valid @RequestBody RegisterStorageBucketRequestBody requestBody);

    /**
     * 检查存储桶并回写结果
     * <li>事务外依次检查：存在、版本控制已开启、写入探测对象、默认加密已生效、读取、删除；实例为 DRAFT 也可检查。</li>
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
     * @return 全部路由，含目标实例代码与存储桶名称
     */
    @GetExchange("/routes")
    StorageRouteListResponseBody listRoutes(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session);

    /**
     * 新建写入路由
     * <li>目标实例与存储桶必须为 ACTIVE，扫描策略必须有 ACTIVE 修订。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param requestBody 来源系统、目标、扫描策略、上传期限与原因
     * @return 保存结果，objectCode 为来源系统
     */
    @PostExchange(url = "/routes", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody createRoute(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @Valid @RequestBody CreateStorageRouteRequestBody requestBody);

    /**
     * 按原版本修改写入路由；只影响之后的新上传
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param sourceSystem 来源系统；默认路由为 *（URL 中可写为 * 或 %2A）
     * @param requestBody 目标、扫描策略、上传期限、原版本与原因
     * @return 保存结果；无变化时版本不变
     */
    @PutExchange(url = "/routes/{sourceSystem}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody updateRoute(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("sourceSystem") String sourceSystem,
        @Valid @RequestBody UpdateStorageRouteRequestBody requestBody);

    /**
     * 按原版本删除写入路由；默认路由 * 不能删除
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param sourceSystem 来源系统
     * @param requestBody 原版本与原因
     * @return 删除前的身份与版本，status 为空
     */
    @DeleteExchange(url = "/routes/{sourceSystem}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ConfigurationMutationResponseBody deleteRoute(@RequestHeader(ACTOR_HEADER) String actor,
        @RequestHeader(SESSION_HEADER) String session, @PathVariable("sourceSystem") String sourceSystem,
        @Valid @RequestBody DeleteStorageRouteRequestBody requestBody);
}
