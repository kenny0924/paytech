package com.paytech.platform.support.filedocument.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 模型定义的封闭上传状态，不使用任意字符串驱动写权限。 */
public enum FileUploadStatus implements CodeEnum {
    CREATED,

    UPLOADING,

    COMPLETED,

    EXPIRED,

    CANCELLED;

    /** 数据库存储稳定业务代码。 */
    @Override public String getCode() { return name(); }
}
