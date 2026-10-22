package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 存储实例类型（t_file_storage_instance.storage_type）；决定使用的 SDK。HTTP 字段使用 String。 */
public enum FileStorageType implements CodeEnum {
    /** MinIO，使用 MinIO SDK；只允许静态凭据。 */
    MINIO("MINIO"),

    /** AWS S3 或兼容 S3 的存储，使用 AWS SDK。 */
    S3("S3");

    private final String code;

    FileStorageType(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
