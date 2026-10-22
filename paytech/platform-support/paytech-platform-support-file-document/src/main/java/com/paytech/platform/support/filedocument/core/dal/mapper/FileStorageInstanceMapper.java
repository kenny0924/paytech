package com.paytech.platform.support.filedocument.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;

/** t_file_storage_instance 的 SQL 映射；SQL 位于 FileStorageInstanceMapper.xml。 */
public interface FileStorageInstanceMapper {

    /** 全部实例，按实例代码排序。 */
    List<FileStorageInstance> selectAll();

    /** 按实例代码读取。 */
    FileStorageInstance selectByCode(@Param("code") String code);

    /** 按实例代码排他锁定当前行。 */
    FileStorageInstance lockByCode(@Param("code") String code);

    /** 插入新实例。 */
    int insert(@Param("row") FileStorageInstance row);

    /** 按原版本修改连接设置。 */
    int updateSettings(@Param("row") FileStorageInstance row, @Param("target") FileStorageInstance target,
        @Param("now") Instant now);

    /** 按原版本替换凭据方式与 Token。 */
    int updateCredential(@Param("row") FileStorageInstance row, @Param("mode") FileStorageCredentialMode mode,
        @Param("token") String token, @Param("now") Instant now);

    /** 按原版本与原状态切换状态。 */
    int updateStatus(@Param("row") FileStorageInstance row, @Param("status") FileStorageInstanceStatus status,
        @Param("now") Instant now);
}
