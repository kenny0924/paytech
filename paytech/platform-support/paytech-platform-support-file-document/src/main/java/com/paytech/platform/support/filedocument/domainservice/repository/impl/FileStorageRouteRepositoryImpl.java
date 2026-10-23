package com.paytech.platform.support.filedocument.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageRoute;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.core.dal.mapper.FileStorageRouteMapper;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageRouteRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的写入路由仓储。 */
@Repository
@AllArgsConstructor
public class FileStorageRouteRepositoryImpl implements FileStorageRouteRepository {

    private final FileStorageRouteMapper mapper;

    /** 全部写入路由。 */
    @Override
    public List<FileStorageRoute> findAll() {
        return mapper.selectAll();
    }

    /** 按来源系统与访问级别排他锁定。 */
    @Override
    public FileStorageRoute lockBySource(String sourceSystem, FileAccessLevel accessLevel) {
        return mapper.lockBySource(sourceSystem, accessLevel);
    }

    /** 引用策略代码的路由数。 */
    @Override
    public int countByPolicyCode(String policyCode) {
        return mapper.countByPolicyCode(policyCode);
    }

    /** 引用策略代码的路由。 */
    @Override
    public List<FileStorageRoute> findByPolicyCode(String policyCode) {
        return mapper.selectByPolicyCode(policyCode);
    }

    /** 指向存储桶的路由数。 */
    @Override
    public int countByBucket(long bucketId) {
        return mapper.countByBucket(bucketId);
    }

    /** 指向实例的路由数。 */
    @Override
    public int countByInstance(long instanceId) {
        return mapper.countByInstance(instanceId);
    }

    /** 只把来源系统唯一键冲突转换为“已存在”业务拒绝，其他故障保留原异常。 */
    @Override
    public void insert(FileStorageRoute route) {
        try {
            requireSaved(mapper.insert(route), route.getSourceSystem());
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_file_storage_route_source")) {
                throw failure;
            }
            throw new BizCheckFailException(
                "写入路由已存在: " + route.getSourceSystem() + "/" + route.getAccessLevel().getCode(), failure);
        }
    }

    /** 条件更新成功后同步目标、版本与时间。 */
    @Override
    public void update(FileStorageRoute route, FileStorageRoute target, Instant now) {
        requireSaved(mapper.update(route, target, now), route.getSourceSystem());
        route.setBucketId(target.getBucketId());
        route.setScanPolicyCode(target.getScanPolicyCode());
        route.setMaxUploadSeconds(target.getMaxUploadSeconds());
        route.setVersion(route.getVersion() + 1);
        route.setUpdateTime(now);
    }

    /** 按原版本删除。 */
    @Override
    public void delete(FileStorageRoute route) {
        requireSaved(mapper.delete(route), route.getSourceSystem());
    }

    /** 未精确保存一行时回滚，由调用方丢弃本次对象。 */
    void requireSaved(int changed, String sourceSystem) {
        if (changed != 1) {
            throw new ConcurrentRequestException("写入路由已被其他操作修改，请刷新后重试: " + sourceSystem);
        }
    }
}
