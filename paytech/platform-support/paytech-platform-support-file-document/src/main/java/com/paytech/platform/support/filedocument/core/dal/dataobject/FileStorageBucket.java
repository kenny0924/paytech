package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;

import lombok.Getter;
import lombok.Setter;

/** t_file_storage_bucket 映射对象；只在 file-document 模块内使用，快照中的对象只读。 */
@Getter
@Setter
public class FileStorageBucket {

    /** 存储桶 Snowflake 主键。 */
    private Long bucketId;

    /** 所属存储实例 ID。 */
    private Long instanceId;

    /**
     * 存储桶名称
     * <li>即文件与上传记录上的 storage_container_ref；创建后不可修改。</li>
     */
    private String bucketName;

    /** 存储桶状态。 */
    private FileStorageBucketStatus status;

    /** 最近一次启用检查时间；从未检查为空。 */
    private Instant lastCheckTime;

    /** 最近一次启用检查结果；从未检查为空。 */
    private FileStorageCheckResult lastCheckResult;

    /** 乐观并发版本。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
