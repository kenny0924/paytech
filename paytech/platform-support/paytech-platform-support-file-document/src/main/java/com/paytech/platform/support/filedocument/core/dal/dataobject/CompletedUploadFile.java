package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;

/**
 * 已完成上传产生的唯一文件，用于同一上传重复完成时返回原结果。
 * @param fileId 该上传产生的文件
 * @param completedAt 文件登记时间
 */
public record CompletedUploadFile(long fileId, Instant completedAt) {}
