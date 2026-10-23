package com.paytech.platform.support.filedocument.domainservice.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageBucket;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageRoute;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.storage.ChangeStorageBucketStatusCommand;
import com.paytech.platform.support.filedocument.domain.storage.ChangeStorageInstanceStatusCommand;
import com.paytech.platform.support.filedocument.domain.storage.CreateStorageInstanceCommand;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageBucketView;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageCatalog;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageField;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageFieldChange;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageInstanceView;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageRouteView;
import com.paytech.platform.support.filedocument.domain.storage.RecordStorageBucketCheckCommand;
import com.paytech.platform.support.filedocument.domain.storage.RegisterStorageBucketCommand;
import com.paytech.platform.support.filedocument.domain.storage.ReplaceStorageCredentialCommand;
import com.paytech.platform.support.filedocument.domain.storage.SaveStorageRouteCommand;
import com.paytech.platform.support.filedocument.domain.storage.StorageInstanceSettings;
import com.paytech.platform.support.filedocument.domain.storage.UpdateStorageBucketCommand;
import com.paytech.platform.support.filedocument.domain.storage.UpdateStorageInstanceCommand;
import com.paytech.platform.support.filedocument.domainservice.repository.FileScanPolicyRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageBucketRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageInstanceRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageRouteRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileUploadRepository;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;

import lombok.AllArgsConstructor;

/**
 * 存储管理的本库规则与保存；不访问 Vault、存储或其他 Service
 * <li>状态转换、引用保护与路由目标资格在锁定当前行后判断，写入以版本与原状态条件更新兜底。</li>
 */
@Service
@AllArgsConstructor
public class FileStorageManagementServiceImpl implements FileStorageManagementService {

    /** 默认路由的来源系统。 */
    public static final String DEFAULT_ROUTE = "*";

    /** 存储桶启用要求最近一次 OK 检查的最长时效。 */
    static final Duration CHECK_VALIDITY = Duration.ofMinutes(10);

    private final Clock clock;
    private final FileUploadRepository uploads;
    private final FileStorageRouteRepository routes;
    private final FileStorageBucketRepository buckets;
    private final FileScanPolicyRepository policies;
    private final FileStorageInstanceRepository instances;

    /** 实例与存储桶按代码、名称排序，路由数按桶统计后汇总到实例。 */
    @Override
    public List<FileStorageInstanceView> listInstances() {
        Map<Long, Long> routeCounts = routes.findAll().stream()
            .collect(Collectors.groupingBy(FileStorageRoute::getBucketId, Collectors.counting()));
        Map<Long, List<FileStorageBucket>> byInstance = buckets.findAll().stream()
            .collect(Collectors.groupingBy(FileStorageBucket::getInstanceId));
        return instances.findAll().stream()
            .map(instance -> instanceView(instance, byInstance.getOrDefault(instance.getInstanceId(), List.of()),
                routeCounts))
            .toList();
    }

    /** 路由按来源系统排序；目标桶或实例缺失属于数据不一致，按空编码返回以便运营发现。 */
    @Override
    public List<FileStorageRouteView> listRoutes() {
        Map<Long, FileStorageBucket> bucketsById = buckets.findAll().stream()
            .collect(Collectors.toMap(FileStorageBucket::getBucketId, Function.identity()));
        Map<Long, FileStorageInstance> instancesById = instances.findAll().stream()
            .collect(Collectors.toMap(FileStorageInstance::getInstanceId, Function.identity()));
        return routes.findAll().stream().map(route -> {
            FileStorageBucket bucket = bucketsById.get(route.getBucketId());
            FileStorageInstance instance = bucket == null ? null : instancesById.get(bucket.getInstanceId());
            return new FileStorageRouteView(route.getSourceSystem(), route.getAccessLevel().getCode(),
                instance == null ? null : instance.getInstanceCode(),
                bucket == null ? null : bucket.getBucketName(), route.getScanPolicyCode(), route.getMaxUploadSeconds(),
                route.getVersion(), route.getUpdateTime());
        }).toList();
    }

    /** 三次读取各自观察已提交状态；快照只用于选择目标，写入路径另行锁定当前行。 */
    @Override
    public FileStorageCatalog loadCatalog() {
        return new FileStorageCatalog(instances.findAll(), buckets.findAll(), routes.findAll(), clock.instant());
    }

    /** 代码已存在时拒绝，避免为必然失败的新建写入 Vault。 */
    @Override
    public void requireInstanceAbsent(String instanceCode) {
        if (instances.findByCode(instanceCode) != null) {
            throw new BizCheckFailException("存储实例已存在: " + instanceCode);
        }
    }

    /** 不存在返回 404，版本不符返回 409。 */
    @Override
    public long requireInstanceVersion(String instanceCode, int expectedVersion) {
        FileStorageInstance instance = instances.findByCode(instanceCode);
        if (instance == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        requireVersion(instance.getVersion(), expectedVersion, "存储实例", instanceCode);
        return instance.getInstanceId();
    }

    /** 插入前校验类型与凭据组合，状态为 DRAFT、版本为 1。 */
    @Override
    public FileStorageChange createInstance(CreateStorageInstanceCommand command) {
        StorageInstanceSettings settings = command.getSettings();
        requireCredentialAllowed(settings.getStorageType(), command.getCredentialMode(), command.getCredentialToken());
        requireEndpointAllowed(settings.getStorageType(), settings.getEndpoint());
        FileStorageInstance instance = new FileStorageInstance();
        instance.setInstanceId(command.getInstanceId());
        instance.setInstanceCode(command.getInstanceCode());
        instance.setStorageType(settings.getStorageType());
        applySettings(instance, settings);
        instance.setCredentialMode(command.getCredentialMode());
        instance.setCredentialVaultToken(command.getCredentialToken());
        instance.setCredentialUpdateTime(command.getNow());
        instance.setStatus(FileStorageInstanceStatus.DRAFT);
        instance.setVersion(1);
        instance.setCreateTime(command.getNow());
        instance.setUpdateTime(command.getNow());
        instances.insert(instance);
        return instanceChange(instance, 0, instanceFields(null, instance));
    }

    /** 类型不可变；MINIO 仍要求地址。无变化时不递增版本。 */
    @Override
    public FileStorageChange updateInstance(UpdateStorageInstanceCommand command) {
        FileStorageInstance instance = lockInstance(command.getInstanceCode(), command.getExpectedVersion());
        StorageInstanceSettings settings = command.getSettings();
        requireEndpointAllowed(instance.getStorageType(), settings.getEndpoint());
        FileStorageInstance target = new FileStorageInstance();
        target.setStorageType(instance.getStorageType());
        applySettings(target, settings);
        List<FileStorageFieldChange> changes = settingChanges(instance, target);
        int before = instance.getVersion();
        if (!changes.isEmpty()) {
            instances.updateSettings(instance, target, command.getNow());
        }
        return instanceChange(instance, before, changes);
    }

    /** 新 Token 替换旧 Token；旧 Token 不再被引用（Vault V1 无 Retire）。 */
    @Override
    public FileStorageChange replaceCredential(ReplaceStorageCredentialCommand command) {
        FileStorageInstance instance = lockInstance(command.getInstanceCode(), command.getExpectedVersion());
        requireCredentialAllowed(instance.getStorageType(), command.getCredentialMode(), command.getCredentialToken());
        int before = instance.getVersion();
        String beforeMode = instance.getCredentialMode().getCode();
        String beforeCredential = instance.getCredentialVaultToken() == null ? null : "SET";
        instances.updateCredential(instance, command.getCredentialMode(), command.getCredentialToken(),
            command.getNow());
        String afterCredential = command.getCredentialToken() == null ? (beforeCredential == null ? null : "CLEARED")
            : (beforeCredential == null ? "SET" : "REPLACED");
        return instanceChange(instance, before, List.of(
            new FileStorageFieldChange(FileStorageField.CREDENTIAL_MODE, beforeMode,
                command.getCredentialMode().getCode()),
            new FileStorageFieldChange(FileStorageField.CREDENTIAL, beforeCredential, afterCredential)));
    }

    /** 只允许 DRAFT→ACTIVE（需有 ACTIVE 桶）、ACTIVE→DISABLED（无引用路由）、DISABLED→ACTIVE。 */
    @Override
    public FileStorageChange changeInstanceStatus(ChangeStorageInstanceStatusCommand command) {
        FileStorageInstance instance = lockInstance(command.getInstanceCode(), command.getExpectedVersion());
        FileStorageInstanceStatus from = instance.getStatus();
        FileStorageInstanceStatus to = command.getStatus();
        requireInstanceTransition(instance, from, to);
        if (from == FileStorageInstanceStatus.DRAFT
            && buckets.countByStatus(instance.getInstanceId(), FileStorageBucketStatus.ACTIVE) == 0) {
            throw new BizCheckFailException("存储实例至少需要一个已启用的存储桶才能启用: " + instance.getInstanceCode());
        }
        if (to == FileStorageInstanceStatus.DISABLED && routes.countByInstance(instance.getInstanceId()) > 0) {
            throw new BizCheckFailException("存储实例的存储桶仍被写入路由引用，不能停用: " + instance.getInstanceCode());
        }
        int before = instance.getVersion();
        instances.updateStatus(instance, to, command.getNow());
        return instanceChange(instance, before,
            List.of(new FileStorageFieldChange(FileStorageField.STATUS, from.getCode(), to.getCode())));
    }

    /** 同实例同名只能登记一次；实例为 DRAFT 也可登记。 */
    @Override
    public FileStorageChange registerBucket(RegisterStorageBucketCommand command) {
        FileStorageInstance instance = lockInstance(command.getInstanceCode(), null);
        FileStorageBucket bucket = new FileStorageBucket();
        bucket.setBucketId(command.getBucketId());
        bucket.setInstanceId(instance.getInstanceId());
        bucket.setBucketName(command.getBucketName());
        bucket.setAccessLevel(command.getAccessLevel());
        bucket.setPublicBaseUrl(command.getPublicBaseUrl());
        bucket.setStatus(FileStorageBucketStatus.DRAFT);
        bucket.setVersion(1);
        bucket.setCreateTime(command.getNow());
        bucket.setUpdateTime(command.getNow());
        buckets.insert(bucket);
        var changes = new ArrayList<FileStorageFieldChange>();
        changes.add(new FileStorageFieldChange(FileStorageField.BUCKET_NAME, null, bucket.getBucketName()));
        changes.add(new FileStorageFieldChange(FileStorageField.ACCESS_LEVEL, null, bucket.getAccessLevel().getCode()));
        diff(changes, FileStorageField.PUBLIC_BASE_URL, null, bucket.getPublicBaseUrl());
        changes.add(new FileStorageFieldChange(FileStorageField.STATUS, null, bucket.getStatus().getCode()));
        return bucketChange(instance, bucket, 0, changes);
    }

    /** 只允许 DRAFT 且 PUBLIC 的桶；地址变化时清除最近检查结果，需重新检查才能启用。无变化时不递增版本。 */
    @Override
    public FileStorageChange updateBucket(UpdateStorageBucketCommand command) {
        FileStorageInstance instance = lockInstance(command.getInstanceCode(), null);
        FileStorageBucket bucket = lockBucket(instance, command.getBucketName(), command.getExpectedVersion());
        if (bucket.getAccessLevel() != FileAccessLevel.PUBLIC) {
            throw new BizCheckFailException("只有公开存储桶可以修改对外基础地址: " + code(instance, bucket));
        }
        if (bucket.getStatus() != FileStorageBucketStatus.DRAFT) {
            throw new BizCheckFailException("对外基础地址只能在存储桶为 DRAFT 时修改: " + code(instance, bucket));
        }
        int before = bucket.getVersion();
        var changes = new ArrayList<FileStorageFieldChange>();
        diff(changes, FileStorageField.PUBLIC_BASE_URL, bucket.getPublicBaseUrl(), command.getPublicBaseUrl());
        if (!changes.isEmpty()) {
            String previousCheck = bucket.getLastCheckResult() == null ? null : bucket.getLastCheckResult().getCode();
            buckets.updatePublicBaseUrl(bucket, command.getPublicBaseUrl(), command.getNow());
            diff(changes, FileStorageField.CHECK_RESULT, previousCheck, null);
        }
        return bucketChange(instance, bucket, before, changes);
    }

    /** 检查结果与时间覆盖上一次结果，版本递增。 */
    @Override
    public FileStorageChange recordBucketCheck(RecordStorageBucketCheckCommand command) {
        FileStorageInstance instance = lockInstance(command.getInstanceCode(), null);
        FileStorageBucket bucket = lockBucket(instance, command.getBucketName(), null);
        int before = bucket.getVersion();
        String previous = bucket.getLastCheckResult() == null ? null : bucket.getLastCheckResult().getCode();
        buckets.updateCheck(bucket, command.getResult(), command.getCheckedAt());
        return bucketChange(instance, bucket, before,
            List.of(new FileStorageFieldChange(FileStorageField.CHECK_RESULT, previous, command.getResult().getCode())));
    }

    /** 启用要求 10 分钟内的 OK 检查；有路由引用时不能设为只读或停用。 */
    @Override
    public FileStorageChange changeBucketStatus(ChangeStorageBucketStatusCommand command) {
        FileStorageInstance instance = lockInstance(command.getInstanceCode(), null);
        FileStorageBucket bucket = lockBucket(instance, command.getBucketName(), command.getExpectedVersion());
        FileStorageBucketStatus from = bucket.getStatus();
        FileStorageBucketStatus to = command.getStatus();
        requireBucketTransition(instance, bucket, from, to);
        if (from == FileStorageBucketStatus.DRAFT && !recentlyChecked(bucket, command.getNow())) {
            throw new BizCheckFailException("存储桶需要 10 分钟内检查通过才能启用: " + code(instance, bucket));
        }
        if (to != FileStorageBucketStatus.ACTIVE && routes.countByBucket(bucket.getBucketId()) > 0) {
            throw new BizCheckFailException("存储桶仍被写入路由引用，不能设为 " + to.getCode() + ": " + code(instance, bucket));
        }
        int before = bucket.getVersion();
        buckets.updateStatus(bucket, to, command.getNow());
        return bucketChange(instance, bucket, before,
            List.of(new FileStorageFieldChange(FileStorageField.STATUS, from.getCode(), to.getCode())));
    }

    /** 目标桶与实例须为 ACTIVE 且桶与路由同访问级别、扫描策略须有 ACTIVE 修订；来源系统与访问级别唯一。 */
    @Override
    public FileStorageChange createRoute(SaveStorageRouteCommand command) {
        FileStorageBucket bucket = lockRouteTarget(command);
        FileStorageRoute route = new FileStorageRoute();
        route.setRouteId(command.getRouteId());
        route.setSourceSystem(command.getSourceSystem());
        route.setAccessLevel(command.getAccessLevel());
        route.setBucketId(bucket.getBucketId());
        route.setScanPolicyCode(command.getScanPolicyCode());
        route.setMaxUploadSeconds(command.getMaxUploadSeconds());
        route.setVersion(1);
        route.setCreateTime(command.getNow());
        route.setUpdateTime(command.getNow());
        routes.insert(route);
        return routeChange(route, 0, routeFields(null, route, command));
    }

    /** 同新建的目标资格；无变化时不递增版本。 */
    @Override
    public FileStorageChange updateRoute(SaveStorageRouteCommand command) {
        FileStorageBucket bucket = lockRouteTarget(command);
        FileStorageRoute route = lockRoute(command.getSourceSystem(), command.getAccessLevel(),
            command.getExpectedVersion());
        FileStorageRoute target = new FileStorageRoute();
        target.setBucketId(bucket.getBucketId());
        target.setScanPolicyCode(command.getScanPolicyCode());
        target.setMaxUploadSeconds(command.getMaxUploadSeconds());
        List<FileStorageFieldChange> changes = routeFields(route, target, command);
        int before = route.getVersion();
        if (!changes.isEmpty()) {
            routes.update(route, target, command.getNow());
        }
        return routeChange(route, before, changes);
    }

    /** 默认私有路由不能删除；删除不影响已创建的上传。 */
    @Override
    public FileStorageChange deleteRoute(String sourceSystem, FileAccessLevel accessLevel, int expectedVersion) {
        if (DEFAULT_ROUTE.equals(sourceSystem) && accessLevel == FileAccessLevel.PRIVATE) {
            throw new BizCheckFailException("默认私有写入路由不能删除");
        }
        FileStorageRoute route = lockRoute(sourceSystem, accessLevel, expectedVersion);
        routes.delete(route);
        return new FileStorageChange(FileStorageChange.ROUTE, route.getRouteId(), routeCode(route), null,
            route.getVersion(), route.getVersion(), List.of(
                new FileStorageFieldChange(FileStorageField.SCAN_POLICY, route.getScanPolicyCode(), null),
                new FileStorageFieldChange(FileStorageField.MAX_UPLOAD_SECONDS,
                    Integer.toString(route.getMaxUploadSeconds()), null)));
    }

    /** 锁定实例；expectedVersion 为空时只要求存在。 */
    FileStorageInstance lockInstance(String instanceCode, Integer expectedVersion) {
        FileStorageInstance instance = instances.lockByCode(instanceCode);
        if (instance == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (expectedVersion != null) {
            requireVersion(instance.getVersion(), expectedVersion, "存储实例", instanceCode);
        }
        return instance;
    }

    /** 锁定实例下的存储桶；expectedVersion 为空时只要求存在。 */
    FileStorageBucket lockBucket(FileStorageInstance instance, String bucketName, Integer expectedVersion) {
        FileStorageBucket bucket = buckets.lockByName(instance.getInstanceId(), bucketName);
        if (bucket == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (expectedVersion != null) {
            requireVersion(bucket.getVersion(), expectedVersion, "存储桶", code(instance, bucket));
        }
        return bucket;
    }

    /** 锁定路由并核对版本；不存在返回 404。 */
    FileStorageRoute lockRoute(String sourceSystem, FileAccessLevel accessLevel, int expectedVersion) {
        FileStorageRoute route = routes.lockBySource(sourceSystem, accessLevel);
        if (route == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        requireVersion(route.getVersion(), expectedVersion, "写入路由", routeCode(route));
        return route;
    }

    /** 按实例 → 桶 → 策略 ACTIVE 修订顺序锁定路由目标，并核对状态、访问级别与扫描策略；策略行锁与停用互斥。 */
    FileStorageBucket lockRouteTarget(SaveStorageRouteCommand command) {
        FileStorageInstance instance = instances.lockByCode(command.getInstanceCode());
        if (instance == null || instance.getStatus() != FileStorageInstanceStatus.ACTIVE) {
            throw new BizCheckFailException("写入路由只能指向已启用的存储实例: " + command.getInstanceCode());
        }
        FileStorageBucket bucket = buckets.lockByName(instance.getInstanceId(), command.getBucketName());
        if (bucket == null || bucket.getStatus() != FileStorageBucketStatus.ACTIVE) {
            throw new BizCheckFailException(
                "写入路由只能指向已启用的存储桶: " + command.getInstanceCode() + "/" + command.getBucketName());
        }
        if (bucket.getAccessLevel() != command.getAccessLevel()) {
            throw new BizCheckFailException("写入路由只能指向同访问级别的存储桶: " + command.getInstanceCode() + "/"
                + command.getBucketName() + " 为 " + bucket.getAccessLevel().getCode() + "，路由为 "
                + command.getAccessLevel().getCode());
        }
        if (policies.lockActive(command.getScanPolicyCode()) == null
            || uploads.policies(command.getScanPolicyCode(), command.getNow()).size() != 1) {
            throw new BizCheckFailException("扫描策略没有唯一的有效修订: " + command.getScanPolicyCode());
        }
        return bucket;
    }

    /** 版本不符返回并发冲突。 */
    void requireVersion(int actual, int expected, String object, String code) {
        if (actual != expected) {
            throw new ConcurrentRequestException(object + "已被其他操作修改，请刷新后重试: " + code + "，当前版本 " + actual
                + "，请求版本 " + expected);
        }
    }

    /** MINIO 只允许 STATIC；STATIC 必须有 Token，IAM_ROLE 不能有 Token。 */
    void requireCredentialAllowed(FileStorageType type, FileStorageCredentialMode mode, String token) {
        if (type == FileStorageType.MINIO && mode != FileStorageCredentialMode.STATIC) {
            throw new BizCheckFailException("MINIO 存储实例只支持静态访问密钥");
        }
        if ((mode == FileStorageCredentialMode.STATIC) != (token != null)) {
            throw new BizCheckFailException("凭据方式与密钥不一致：STATIC 必须提供密钥，IAM_ROLE 不能提供密钥");
        }
    }

    /** MINIO 必须提供地址。 */
    void requireEndpointAllowed(FileStorageType type, String endpoint) {
        if (type == FileStorageType.MINIO && endpoint == null) {
            throw new BizCheckFailException("MINIO 存储实例必须填写服务地址");
        }
    }

    /** 允许的实例状态转换。 */
    void requireInstanceTransition(FileStorageInstance instance, FileStorageInstanceStatus from,
        FileStorageInstanceStatus to) {
        boolean allowed = (from == FileStorageInstanceStatus.DRAFT && to == FileStorageInstanceStatus.ACTIVE)
            || (from == FileStorageInstanceStatus.ACTIVE && to == FileStorageInstanceStatus.DISABLED)
            || (from == FileStorageInstanceStatus.DISABLED && to == FileStorageInstanceStatus.ACTIVE);
        if (!allowed) {
            throw new BizCheckFailException("存储实例状态不能从 " + from.getCode() + " 变为 " + to.getCode() + ": "
                + instance.getInstanceCode());
        }
    }

    /** 允许的存储桶状态转换。 */
    void requireBucketTransition(FileStorageInstance instance, FileStorageBucket bucket, FileStorageBucketStatus from,
        FileStorageBucketStatus to) {
        boolean allowed = switch (from) {
            case DRAFT -> to == FileStorageBucketStatus.ACTIVE;
            case ACTIVE -> to == FileStorageBucketStatus.READ_ONLY || to == FileStorageBucketStatus.DISABLED;
            case READ_ONLY -> to == FileStorageBucketStatus.ACTIVE || to == FileStorageBucketStatus.DISABLED;
            case DISABLED -> false;
        };
        if (!allowed) {
            throw new BizCheckFailException("存储桶状态不能从 " + from.getCode() + " 变为 " + to.getCode() + ": "
                + code(instance, bucket));
        }
    }

    /** 最近一次检查为 OK 且不早于 10 分钟前。 */
    boolean recentlyChecked(FileStorageBucket bucket, Instant now) {
        return bucket.getLastCheckResult() == FileStorageCheckResult.OK && bucket.getLastCheckTime() != null
            && !bucket.getLastCheckTime().isBefore(now.minus(CHECK_VALIDITY));
    }

    /** 写入可编辑的连接设置。 */
    void applySettings(FileStorageInstance instance, StorageInstanceSettings settings) {
        instance.setEndpoint(settings.getEndpoint());
        instance.setRegion(settings.getRegion());
        instance.setPathStyleAccess(settings.isPathStyleAccess());
        instance.setTrustedCertificatePem(settings.getTrustedCertificatePem());
        instance.setConnectTimeoutMs(settings.getConnectTimeoutMs());
        instance.setReadTimeoutMs(settings.getReadTimeoutMs());
    }

    /** 新建实例的全部字段；修改前为空。 */
    List<FileStorageFieldChange> instanceFields(FileStorageInstance before, FileStorageInstance after) {
        var changes = new ArrayList<FileStorageFieldChange>();
        changes.add(new FileStorageFieldChange(FileStorageField.CODE, null, after.getInstanceCode()));
        changes.add(new FileStorageFieldChange(FileStorageField.STORAGE_TYPE, null, after.getStorageType().getCode()));
        changes.addAll(settingChanges(before, after));
        changes.add(new FileStorageFieldChange(FileStorageField.CREDENTIAL_MODE, null,
            after.getCredentialMode().getCode()));
        changes.add(new FileStorageFieldChange(FileStorageField.CREDENTIAL, null,
            after.getCredentialVaultToken() == null ? null : "SET"));
        changes.add(new FileStorageFieldChange(FileStorageField.STATUS, null, after.getStatus().getCode()));
        return changes;
    }

    /** 连接设置的差异；before 为空时视为全部新增。证书只记录摘要。 */
    List<FileStorageFieldChange> settingChanges(FileStorageInstance before, FileStorageInstance after) {
        var changes = new ArrayList<FileStorageFieldChange>();
        diff(changes, FileStorageField.ENDPOINT, before == null ? null : before.getEndpoint(), after.getEndpoint());
        diff(changes, FileStorageField.REGION, before == null ? null : before.getRegion(), after.getRegion());
        diff(changes, FileStorageField.PATH_STYLE_ACCESS, before == null ? null : before.getPathStyleAccess().toString(),
            after.getPathStyleAccess().toString());
        diff(changes, FileStorageField.TRUSTED_CERTIFICATE_DIGEST,
            before == null ? null : digest(before.getTrustedCertificatePem()), digest(after.getTrustedCertificatePem()));
        diff(changes, FileStorageField.CONNECT_TIMEOUT_MILLIS,
            before == null ? null : before.getConnectTimeoutMs().toString(), after.getConnectTimeoutMs().toString());
        diff(changes, FileStorageField.READ_TIMEOUT_MILLIS, before == null ? null : before.getReadTimeoutMs().toString(),
            after.getReadTimeoutMs().toString());
        return changes;
    }

    /** 路由字段差异；before 为空表示新建。原目标只记录桶主键，避免为审计再读取其他行。 */
    List<FileStorageFieldChange> routeFields(FileStorageRoute before, FileStorageRoute after,
        SaveStorageRouteCommand command) {
        var changes = new ArrayList<FileStorageFieldChange>();
        if (before == null) {
            changes.add(new FileStorageFieldChange(FileStorageField.CODE, null, command.getSourceSystem()));
            changes.add(new FileStorageFieldChange(FileStorageField.ACCESS_LEVEL, null,
                command.getAccessLevel().getCode()));
        }
        if (before == null || !Objects.equals(before.getBucketId(), after.getBucketId())) {
            changes.add(new FileStorageFieldChange(FileStorageField.STORAGE_INSTANCE, null, command.getInstanceCode()));
            changes.add(new FileStorageFieldChange(FileStorageField.STORAGE_BUCKET,
                before == null ? null : before.getBucketId().toString(), command.getBucketName()));
        }
        diff(changes, FileStorageField.SCAN_POLICY, before == null ? null : before.getScanPolicyCode(),
            after.getScanPolicyCode());
        diff(changes, FileStorageField.MAX_UPLOAD_SECONDS,
            before == null ? null : before.getMaxUploadSeconds().toString(), after.getMaxUploadSeconds().toString());
        return changes;
    }

    /** 值不同才记录。 */
    void diff(List<FileStorageFieldChange> changes, FileStorageField field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new FileStorageFieldChange(field, before, after));
        }
    }

    /** 证书 PEM 的 SHA-256 小写十六进制摘要；为空时为空。 */
    static String digest(String value) {
        if (value == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 实例变更结果。 */
    FileStorageChange instanceChange(FileStorageInstance instance, int before, List<FileStorageFieldChange> changes) {
        return new FileStorageChange(FileStorageChange.INSTANCE, instance.getInstanceId(), instance.getInstanceCode(),
            instance.getStatus().getCode(), before, instance.getVersion(), changes);
    }

    /** 存储桶变更结果。 */
    FileStorageChange bucketChange(FileStorageInstance instance, FileStorageBucket bucket, int before,
        List<FileStorageFieldChange> changes) {
        return new FileStorageChange(FileStorageChange.BUCKET, bucket.getBucketId(), code(instance, bucket),
            bucket.getStatus().getCode(), before, bucket.getVersion(), changes);
    }

    /** 路由变更结果。 */
    FileStorageChange routeChange(FileStorageRoute route, int before, List<FileStorageFieldChange> changes) {
        return new FileStorageChange(FileStorageChange.ROUTE, route.getRouteId(), routeCode(route), null, before,
            route.getVersion(), changes);
    }

    /** 路由的审计编码：{来源系统}/{访问级别}。 */
    static String routeCode(FileStorageRoute route) {
        return route.getSourceSystem() + "/" + route.getAccessLevel().getCode();
    }

    /** 存储桶的审计编码。 */
    static String code(FileStorageInstance instance, FileStorageBucket bucket) {
        return instance.getInstanceCode() + "/" + bucket.getBucketName();
    }

    /** 组装实例列表项。 */
    FileStorageInstanceView instanceView(FileStorageInstance instance, List<FileStorageBucket> ownBuckets,
        Map<Long, Long> routeCounts) {
        List<FileStorageBucketView> bucketViews = ownBuckets.stream()
            .sorted(Comparator.comparing(FileStorageBucket::getBucketName))
            .map(bucket -> new FileStorageBucketView(bucket.getBucketName(), bucket.getAccessLevel().getCode(),
                bucket.getPublicBaseUrl(), bucket.getStatus().getCode(),
                bucket.getLastCheckTime(),
                bucket.getLastCheckResult() == null ? null : bucket.getLastCheckResult().getCode(),
                Math.toIntExact(routeCounts.getOrDefault(bucket.getBucketId(), 0L)), bucket.getVersion(),
                bucket.getUpdateTime()))
            .toList();
        int routeCount = bucketViews.stream().mapToInt(FileStorageBucketView::getRouteCount).sum();
        return new FileStorageInstanceView(instance.getInstanceCode(), instance.getStorageType().getCode(),
            instance.getEndpoint(), instance.getRegion(), instance.getPathStyleAccess(),
            instance.getTrustedCertificatePem(), instance.getCredentialMode().getCode(),
            instance.getCredentialVaultToken() != null, instance.getCredentialUpdateTime(),
            instance.getConnectTimeoutMs(), instance.getReadTimeoutMs(), instance.getStatus().getCode(),
            instance.getVersion(), instance.getUpdateTime(), routeCount, bucketViews);
    }
}
