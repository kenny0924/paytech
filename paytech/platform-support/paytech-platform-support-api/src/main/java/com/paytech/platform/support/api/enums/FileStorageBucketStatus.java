package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 存储桶状态（t_file_storage_bucket.status）。HTTP 字段使用 String。 */
public enum FileStorageBucketStatus implements CodeEnum {
    /** 已登记，未投入使用。 */
    DRAFT("DRAFT"),

    /** 接受新上传，可读。 */
    ACTIVE("ACTIVE"),

    /** 只读，不接受新上传。 */
    READ_ONLY("READ_ONLY"),

    /** 已停用，读写都返回不可用。 */
    DISABLED("DISABLED");

    private final String code;

    FileStorageBucketStatus(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
