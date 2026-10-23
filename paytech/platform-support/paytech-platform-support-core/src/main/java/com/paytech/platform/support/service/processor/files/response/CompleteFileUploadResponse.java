package com.paytech.platform.support.service.processor.files.response;

import java.time.Instant;

import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成上传的回执；重复完成返回同一组值。 */
@Getter
@AllArgsConstructor
public class CompleteFileUploadResponse {

    /** 上传 ID。 */
    private final long uploadId;

    /** 登记的文件 ID；替换上传为被替换的文件。 */
    private final long fileId;

    /** 文件或版本登记时间。 */
    private final Instant completedAt;

    /** 本次上传产生的版本号；首次上传为 1。 */
    private final int versionNo;

    /** 访问级别。 */
    private final FileAccessLevel accessLevel;
}
