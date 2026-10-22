package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一次存储桶启用检查的结果；已回写到存储桶。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StorageBucketCheckResponseBody {

    /** 实例代码。 */
    private String instanceCode;

    /** 存储桶名称。 */
    private String bucketName;

    /**
     * 检查结果
     * <li>{@code FileStorageCheckResult} Code：OK、NOT_FOUND、VERSIONING_DISABLED、ENCRYPTION_DISABLED、ACCESS_DENIED、UNAVAILABLE。</li>
     */
    private String result;

    /** 检查完成时间（UTC）。 */
    private Instant checkedAt;

    /** 回写后存储桶的版本，启用时作为原版本。 */
    private int version;
}
