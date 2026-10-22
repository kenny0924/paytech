package com.paytech.platform.support.service.processor.filestorage.response;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次存储桶检查的结果与回写后的存储桶身份、版本。 */
@Getter
@AllArgsConstructor
public class StorageBucketCheckOutcome {

    /** 回写结果的保存差异。 */
    private final FileStorageChange change;

    /** 检查结果。 */
    private final FileStorageCheckResult result;

    /** 检查完成时间。 */
    private final Instant checkedAt;
}
