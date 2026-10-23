package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本修改 DRAFT 公开桶的对外基础地址。 */
@Getter
@AllArgsConstructor
public class UpdateStorageBucketCommand {

    /** 所属实例代码。 */
    private final String instanceCode;

    /** 存储桶名称。 */
    private final String bucketName;

    /** 新的对外基础地址；调用方已校验格式。 */
    private final String publicBaseUrl;

    /** 调用方读取的原版本。 */
    private final int expectedVersion;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;
}
