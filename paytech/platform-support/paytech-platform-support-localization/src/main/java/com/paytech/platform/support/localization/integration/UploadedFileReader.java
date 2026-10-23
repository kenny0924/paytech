package com.paytech.platform.support.localization.integration;

/**
 * 读取经文件服务上传的导入文件（技术方案 3.5）；在事务外调用。
 * <li>文件须为本模块的上传原因码、扫描 AVAILABLE、对象 ACTIVE，且不超过 localization.import-max-file-bytes。</li>
 */
public interface UploadedFileReader {

    /**
     * 按文件 ID 读取。
     *
     * @param fileId 文件 ID
     * @param reasonCode 期望的上传原因码，I18N_KEY_IMPORT 或 I18N_TRANSLATION_IMPORT
     * @return 文件内容
     * @throws com.paytech.foundation.common.error.FailException 仍在检查（I18N_IMPORT_FILE_NOT_READY，409）
     * @throws com.paytech.foundation.common.error.BizCheckFailException 文件不存在、原因码不符、检查被拒绝或隔离、对象不可用或过大
     * @throws com.paytech.foundation.common.error.ServiceUnavailableException 存储不可读
     */
    UploadedFile read(long fileId, String reasonCode);
}
