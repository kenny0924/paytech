package com.paytech.platform.support.filedocument.domain.storage;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 新建检查策略（修订 1）、以 ACTIVE 为模板新建修订或按原版本修改草稿
 * <li>新建时 policyId、requirementId 为预先生成的主键；修改时不使用。</li>
 * <li>以模板新建修订时 allowedMimeTypes 为空、maxSizeBytes 为 0，内容取 ACTIVE 修订。</li>
 */
@Getter
@AllArgsConstructor
public class SaveScanPolicyRevisionCommand {

    /** 新修订主键；修改草稿时为 0。 */
    private final long policyId;

    /** 新修订的格式检查要求主键；修改草稿时为 0。 */
    private final long requirementId;

    /** 策略代码。 */
    private final String policyCode;

    /** 修改的修订号；新建时为 0。 */
    private final int revisionNo;

    /** 允许的规范 MIME；调用方已去重并核对受支持。 */
    private final List<String> allowedMimeTypes;

    /** 最大字节数；调用方已核对范围。 */
    private final long maxSizeBytes;

    /** 修改时调用方读取的原版本；新建为 0。 */
    private final int expectedVersion;

    /** 本次保存时间（UTC 微秒）。 */
    private final Instant now;
}
