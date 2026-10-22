package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 在存储实例下登记 DRAFT 存储桶；Platform 不建桶。 */
@Getter
@AllArgsConstructor
public class RegisterStorageBucketCommand {

    /** 预先生成的存储桶主键。 */
    private final long bucketId;

    /** 所属实例代码。 */
    private final String instanceCode;

    /** 存储桶名称。 */
    private final String bucketName;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;
}
