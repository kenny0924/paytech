package com.paytech.platform.support.filedocument.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageBucket;

/** t_file_storage_bucket 的 SQL 映射；SQL 位于 FileStorageBucketMapper.xml。 */
public interface FileStorageBucketMapper {

    /** 全部存储桶，按实例与名称排序。 */
    List<FileStorageBucket> selectAll();

    /** 按实例与名称读取。 */
    FileStorageBucket selectByName(@Param("instanceId") long instanceId, @Param("bucketName") String bucketName);

    /** 按实例与名称排他锁定当前行。 */
    FileStorageBucket lockByName(@Param("instanceId") long instanceId, @Param("bucketName") String bucketName);

    /** 统计实例下指定状态的存储桶数。 */
    int countByStatus(@Param("instanceId") long instanceId, @Param("status") FileStorageBucketStatus status);

    /** 插入新存储桶。 */
    int insert(@Param("row") FileStorageBucket row);

    /** 回写检查结果并递增版本。 */
    int updateCheck(@Param("row") FileStorageBucket row, @Param("result") FileStorageCheckResult result,
        @Param("checkedAt") Instant checkedAt);

    /** 按原版本修改 DRAFT 公开桶的对外基础地址并清除最近检查结果。 */
    int updatePublicBaseUrl(@Param("row") FileStorageBucket row, @Param("publicBaseUrl") String publicBaseUrl,
        @Param("now") Instant now);

    /** 按原版本与原状态切换状态。 */
    int updateStatus(@Param("row") FileStorageBucket row, @Param("status") FileStorageBucketStatus status,
        @Param("now") Instant now);
}
