package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 写入路由列表的一项，含目标实例代码与存储桶名称。 */
@Getter
@AllArgsConstructor
public class FileStorageRouteView {

    /** 来源系统或 *。 */
    private final String sourceSystem;

    /** 访问级别 Code。 */
    private final String accessLevel;

    /** 目标实例代码。 */
    private final String instanceCode;

    /** 目标存储桶名称。 */
    private final String bucketName;

    /** 扫描策略代码。 */
    private final String scanPolicyCode;

    /** 上传授权有效秒数。 */
    private final int maxUploadSeconds;

    /** 乐观并发版本。 */
    private final int version;

    /** 最后更新时间。 */
    private final Instant updateTime;
}
