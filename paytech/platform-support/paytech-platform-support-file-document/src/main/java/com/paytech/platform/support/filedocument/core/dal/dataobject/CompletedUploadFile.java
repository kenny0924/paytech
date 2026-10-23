package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;

/**
 * 已完成上传产生的唯一文件版本，用于同一上传重复完成时返回原结果。
 * @param fileId 该上传产生（或替换）的文件
 * @param versionNo 该上传产生的版本号
 * @param completedAt 版本登记时间
 */
public record CompletedUploadFile(long fileId, int versionNo, Instant completedAt) {}
