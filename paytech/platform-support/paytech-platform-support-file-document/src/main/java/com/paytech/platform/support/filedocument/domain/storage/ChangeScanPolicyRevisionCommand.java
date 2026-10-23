package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本废弃草稿、激活草稿或停用 ACTIVE 修订。 */
@Getter
@AllArgsConstructor
public class ChangeScanPolicyRevisionCommand {

    /** 策略代码。 */
    private final String policyCode;

    /** 目标修订号；停用作用于当前 ACTIVE 修订，为空。 */
    private final Integer revisionNo;

    /** 调用方读取的目标修订原版本。 */
    private final int expectedVersion;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;
}
