package com.paytech.operations.console.bff.service.processor;

import java.util.Objects;

import com.paytech.operations.console.bff.integration.PlatformPublicFileClient;
import com.paytech.operations.console.bff.service.model.fileupload.response.FileUploadRecordResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

/** 文件上传页的列表与详情共用的页面模型转换与入口归属判断；纯计算。 */
final class FileUploadRecords {

    /** 仅作为静态方法命名空间。 */
    private FileUploadRecords() {
    }

    /** 文件是否由本入口上传：来源为本 BFF 且原因为 OPERATIONS_PUBLIC_ASSET。 */
    static boolean fromEntry(StoredFileResponseBody file) {
        return PlatformPublicFileClient.SOURCE_SYSTEM.equals(file.getSourceSystem())
            && PlatformPublicFileClient.REASON_CODE.equals(file.getReasonCode());
    }

    /**
     * Platform 元数据转为页面记录；上传人与当前会话账号相同时标记为本人上传
     *
     * @param file Platform 文件元数据
     * @param accountId 当前会话账号
     * @return 页面记录
     */
    static FileUploadRecordResponseBody record(StoredFileResponseBody file, String accountId) {
        return new FileUploadRecordResponseBody(file.getFileId(), file.getFileAddress(), file.getDisplayName(),
            file.getMimeType(), file.getSizeBytes(), file.getScanStatus(), file.getVersionNo(),
            file.getUploaderAccountId(), Objects.equals(accountId, file.getUploaderAccountId()), file.getCreateTime(),
            file.getPublicUrl(), file.getVersionedUrl());
    }
}
