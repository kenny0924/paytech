package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileStorageBucketStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本切换存储桶状态。 */
@Getter
@AllArgsConstructor
public class ChangeStorageBucketStatusCommand {

    /** 所属实例代码。 */
    private final String instanceCode;

    /** 存储桶名称。 */
    private final String bucketName;

    /** 目标状态。 */
    private final FileStorageBucketStatus status;

    /** 调用方读取的原版本。 */
    private final int expectedVersion;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;
}
