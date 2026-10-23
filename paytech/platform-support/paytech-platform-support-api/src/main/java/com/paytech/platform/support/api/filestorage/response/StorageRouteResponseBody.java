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

    /**
     * 访问级别
     * <li>PRIVATE 或 PUBLIC；与来源系统共同构成路由键，只指向同级别的桶。</li>
     */
    private String accessLevel;

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
