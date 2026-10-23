package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建或按原版本修改写入路由。 */
@Getter
@AllArgsConstructor
public class SaveStorageRouteCommand {

    /** 新建时预先生成的路由主键；修改时不使用。 */
    private final long routeId;

    /** 来源系统或 *。 */
    private final String sourceSystem;

    /** 访问级别；与来源系统共同构成路由键，目标桶必须为同一级别。 */
    private final FileAccessLevel accessLevel;

    /** 目标实例代码。 */
    private final String instanceCode;

    /** 目标存储桶名称。 */
    private final String bucketName;

    /** 扫描策略代码。 */
    private final String scanPolicyCode;

    /** 上传授权有效秒数。 */
    private final int maxUploadSeconds;

    /** 修改时调用方读取的原版本；新建为 0。 */
    private final int expectedVersion;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;
}
