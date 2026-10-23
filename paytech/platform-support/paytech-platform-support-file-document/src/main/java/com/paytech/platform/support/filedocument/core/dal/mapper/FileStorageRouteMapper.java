package com.paytech.platform.support.filedocument.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageRoute;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

/** t_file_storage_route 的 SQL 映射；SQL 位于 FileStorageRouteMapper.xml。 */
public interface FileStorageRouteMapper {

    /** 全部写入路由，按来源系统与访问级别排序。 */
    List<FileStorageRoute> selectAll();

    /** 按来源系统与访问级别排他锁定当前行。 */
    FileStorageRoute lockBySource(@Param("sourceSystem") String sourceSystem,
        @Param("accessLevel") FileAccessLevel accessLevel);

    /** 统计引用某扫描策略代码的路由数。 */
    int countByPolicyCode(@Param("policyCode") String policyCode);

    /** 引用某扫描策略代码的路由，按来源系统与访问级别排序。 */
    List<FileStorageRoute> selectByPolicyCode(@Param("policyCode") String policyCode);

    /** 统计指向某存储桶的路由数。 */
    int countByBucket(@Param("bucketId") long bucketId);

    /** 统计指向某实例任一存储桶的路由数。 */
    int countByInstance(@Param("instanceId") long instanceId);

    /** 插入新路由。 */
    int insert(@Param("row") FileStorageRoute row);

    /** 按原版本修改目标桶、扫描策略与上传期限。 */
    int update(@Param("row") FileStorageRoute row, @Param("target") FileStorageRoute target,
        @Param("now") Instant now);

    /** 按原版本删除路由。 */
    int delete(@Param("row") FileStorageRoute row);
}
