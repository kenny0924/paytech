package com.paytech.platform.support.filedocument.domain.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个存储管理字段的前后值；不含密钥、Token 或证书原文。 */
@Getter
@AllArgsConstructor
public class FileStorageFieldChange {

    /** 字段。 */
    private final FileStorageField field;

    /** 修改前的值；新建为空。 */
    private final String before;

    /** 修改后的值；删除为空。 */
    private final String after;
}
