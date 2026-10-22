package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 存储实例列表中的一个存储桶及引用它的路由数。 */
@Getter
@AllArgsConstructor
public class FileStorageBucketView {

    /** 存储桶名称。 */
    private final String bucketName;

    /** 状态 Code。 */
    private final String status;

    /** 最近一次检查时间；可空。 */
    private final Instant lastCheckTime;

    /** 最近一次检查结果 Code；可空。 */
    private final String lastCheckResult;

    /** 引用该桶的写入路由数。 */
    private final int routeCount;

    /** 乐观并发版本。 */
    private final int version;

    /** 最后更新时间。 */
    private final Instant updateTime;
}
