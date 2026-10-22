package com.paytech.platform.support.filedocument.domain.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按来源系统路由得到的新上传目标；上传创建时固定到记录，之后不受路由变化影响。 */
@Getter
@AllArgsConstructor
public class StorageWriteTarget {

    /** 实例代码，即记录上的 storage_provider。 */
    private final String provider;

    /** 存储桶名称，即记录上的 storage_container_ref。 */
    private final String container;

    /** 新上传冻结的扫描策略代码。 */
    private final String scanPolicyCode;

    /** 上传授权有效秒数。 */
    private final int maxUploadSeconds;
}
