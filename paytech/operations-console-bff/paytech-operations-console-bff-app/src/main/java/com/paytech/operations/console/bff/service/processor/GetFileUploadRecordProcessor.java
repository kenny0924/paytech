package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.PlatformPublicFileClient;
import com.paytech.operations.console.bff.service.model.fileupload.response.FileUploadRecordResponseBody;
import com.paytech.operations.console.bff.service.processor.request.GetFileUploadRecordRequest;
import com.paytech.platform.support.api.enums.FileErrorCode;

import lombok.AllArgsConstructor;

/**
 * 文件上传页查询单个上传：读取 Platform 元数据后核对来源与原因，只返回本入口的上传
 * <li>Platform 内部按 ID 查询不做文件级权限；不核对时只有上传权限的人员能读到任意文件的元数据，因此其他文件一律按不存在（404）处理。</li>
 */
@Component
@AllArgsConstructor
public class GetFileUploadRecordProcessor
    extends AbstractProcessor<GetFileUploadRecordRequest, FileUploadRecordResponseBody> {
    private final PlatformPublicFileClient files;

    @Override
    protected FileUploadRecordResponseBody processRequest(GetFileUploadRecordRequest request) {
        var file = files.file(request.getFileId());
        if (!FileUploadRecords.fromEntry(file)) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        return FileUploadRecords.record(file, request.getOperator().accountId());
    }
}
