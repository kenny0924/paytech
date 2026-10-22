package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本修改存储实例的连接设置；类型、代码与凭据不在此修改。 */
@Getter
@AllArgsConstructor
public class UpdateStorageInstanceCommand {

    /** 实例代码。 */
    private final String instanceCode;

    /** 目标连接设置；storageType 取原值。 */
    private final StorageInstanceSettings settings;

    /** 调用方读取的原版本。 */
    private final int expectedVersion;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;
}
