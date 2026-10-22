package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 存储实例状态（t_file_storage_instance.status）；只有 ACTIVE 实例可读写。HTTP 字段使用 String。 */
public enum FileStorageInstanceStatus implements CodeEnum {
    /** 草稿，未投入使用。 */
    DRAFT("DRAFT"),

    /** 可读写。 */
    ACTIVE("ACTIVE"),

    /** 已停用，读写都返回不可用。 */
    DISABLED("DISABLED");

    private final String code;

    FileStorageInstanceStatus(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
