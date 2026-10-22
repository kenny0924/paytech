package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileStorageCheckResult;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 回写事务外完成的一次存储桶启用检查。 */
@Getter
@AllArgsConstructor
public class RecordStorageBucketCheckCommand {

    /** 所属实例代码。 */
    private final String instanceCode;

    /** 存储桶名称。 */
    private final String bucketName;

    /** 检查结果。 */
    private final FileStorageCheckResult result;

    /** 检查完成时间（UTC 微秒）。 */
    private final Instant checkedAt;
}
