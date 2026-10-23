package com.paytech.platform.support.filedocument.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageRoute;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

/** 写入路由表仓储；条件更新成功后同步传入的原对象。 */
public interface FileStorageRouteRepository {

    /** 全部写入路由，按来源系统与访问级别排序。 */
    List<FileStorageRoute> findAll();

    /** 按来源系统与访问级别排他锁定；不存在为空。 */
    FileStorageRoute lockBySource(String sourceSystem, FileAccessLevel accessLevel);

    /** 引用某扫描策略代码的路由数。 */
    int countByPolicyCode(String policyCode);

    /** 引用某扫描策略代码的路由。 */
    List<FileStorageRoute> findByPolicyCode(String policyCode);

    /** 指向某存储桶的路由数。 */
    int countByBucket(long bucketId);

    /** 指向某实例任一存储桶的路由数。 */
    int countByInstance(long instanceId);

    /** 插入新路由；同来源同级别已存在转换为业务拒绝。 */
    void insert(FileStorageRoute route);

    /** 按原版本修改目标桶、扫描策略与上传期限。 */
    void update(FileStorageRoute route, FileStorageRoute target, Instant now);

    /** 按原版本删除路由。 */
    void delete(FileStorageRoute route);
}
