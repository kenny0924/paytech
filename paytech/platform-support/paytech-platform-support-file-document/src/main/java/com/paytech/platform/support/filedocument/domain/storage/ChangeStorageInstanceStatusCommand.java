package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本切换存储实例状态。 */
@Getter
@AllArgsConstructor
public class ChangeStorageInstanceStatusCommand {

    /** 实例代码。 */
    private final String instanceCode;

    /** 目标状态。 */
    private final FileStorageInstanceStatus status;

    /** 调用方读取的原版本。 */
    private final int expectedVersion;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;
}
