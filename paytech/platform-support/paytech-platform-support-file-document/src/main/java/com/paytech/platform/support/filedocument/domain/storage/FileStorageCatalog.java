package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageBucket;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageRoute;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

/**
 * 一次读取的存储实例、存储桶与写入路由全集；只在 file-document 模块内使用，不含密钥原值
 * <li>作为进程内快照时其中的对象只读，任何写入都从数据库重新读取并锁定当前行。</li>
 */
public final class FileStorageCatalog {

    private final Map<String, FileStorageInstance> instances;
    private final Map<Long, FileStorageInstance> instancesById;
    private final Map<Long, FileStorageBucket> buckets;
    private final Map<String, FileStorageRoute> routes;
    private final Instant loadedAt;

    /** 以三张表的全部行构造索引。 */
    public FileStorageCatalog(List<FileStorageInstance> instances, List<FileStorageBucket> buckets,
        List<FileStorageRoute> routes, Instant loadedAt) {
        this.instances = instances.stream()
            .collect(Collectors.toUnmodifiableMap(FileStorageInstance::getInstanceCode, Function.identity()));
        this.instancesById = instances.stream()
            .collect(Collectors.toUnmodifiableMap(FileStorageInstance::getInstanceId, Function.identity()));
        this.buckets = buckets.stream()
            .collect(Collectors.toUnmodifiableMap(FileStorageBucket::getBucketId, Function.identity()));
        this.routes = routes.stream()
            .collect(Collectors.toUnmodifiableMap(route -> routeKey(route.getSourceSystem(), route.getAccessLevel()),
                Function.identity()));
        this.loadedAt = loadedAt;
    }

    /** 按实例代码查找；不存在为空。 */
    public FileStorageInstance instance(String instanceCode) {
        return instances.get(instanceCode);
    }

    /** 按实例主键查找；不存在为空。 */
    public FileStorageInstance instance(long instanceId) {
        return instancesById.get(instanceId);
    }

    /** 按实例与名称查找存储桶；不存在为空。 */
    public FileStorageBucket bucket(long instanceId, String bucketName) {
        return buckets.values().stream()
            .filter(bucket -> bucket.getInstanceId() == instanceId && bucket.getBucketName().equals(bucketName))
            .findFirst().orElse(null);
    }

    /** 按主键查找存储桶；不存在为空。 */
    public FileStorageBucket bucket(long bucketId) {
        return buckets.get(bucketId);
    }

    /** 来源系统在该访问级别的精确路由；不存在为空。 */
    public FileStorageRoute route(String sourceSystem, FileAccessLevel accessLevel) {
        return routes.get(routeKey(sourceSystem, accessLevel));
    }

    /** 路由索引键：来源系统与访问级别。 */
    private static String routeKey(String sourceSystem, FileAccessLevel accessLevel) {
        return sourceSystem + "|" + accessLevel.getCode();
    }

    /** 加载时间。 */
    public Instant loadedAt() {
        return loadedAt;
    }
}
