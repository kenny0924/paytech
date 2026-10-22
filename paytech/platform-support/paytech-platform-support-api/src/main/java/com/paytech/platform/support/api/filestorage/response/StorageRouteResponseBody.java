package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一条写入路由。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StorageRouteResponseBody {

    /** 来源系统，或 * 表示默认路由。 */
    private String sourceSystem;

    /** 目标实例代码。 */
    private String instanceCode;

    /** 目标存储桶名称。 */
    private String bucketName;

    /** 扫描策略代码。 */
    private String scanPolicyCode;

    /** 上传授权有效秒数。 */
    private int maxUploadSeconds;

    /** 乐观并发版本。 */
    private int version;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
