package com.paytech.operations.console.bff.service.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.PlatformPublicFileClient;
import com.paytech.operations.console.bff.service.processor.request.CompleteFileUploadRecordRequest;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;

import lombok.AllArgsConstructor;

/**
 * 文件上传页完成上传：转发 Platform 完成上传后，按回执的文件 ID 复查元数据，只把本入口的公开上传回执返回页面
 * <li>Platform 完成上传只核对来源系统，不核对原因；卡 BIN、多语言导入等同来源的上传 ID 也能经本入口完成。</li>
 * <li>复查要求来源 operations-console-bff、原因 OPERATIONS_PUBLIC_ASSET、访问级别 PUBLIC；不符时按文件不存在（404）处理并记 WARN，
 * 不外露其他业务的文件信息。完成已在 Platform 发生且幂等，无法撤回，原业务再完成会得到同一回执。</li>
 */
@Component
@AllArgsConstructor
public class CompleteFileUploadRecordProcessor
    extends AbstractProcessor<CompleteFileUploadRecordRequest, FileUploadCompletionResponseBody> {
    private static final Logger LOG = LoggerFactory.getLogger(CompleteFileUploadRecordProcessor.class);

    private final PlatformPublicFileClient files;

    @Override
    protected FileUploadCompletionResponseBody processRequest(CompleteFileUploadRecordRequest request) {
        // 完成上传
        FileUploadCompletionResponseBody completion = files.completeUpload(request.getUploadId());

        // 复查完成的文件属于本入口
        var file = files.file(completion.getFileId());
        if (!FileUploadRecords.fromEntry(file) || !PlatformPublicFileClient.ACCESS_LEVEL.equals(file.getAccessLevel())) {
            LOG.warn("Completed upload is not a public upload of this entry: uploadId={}, fileId={}",
                request.getUploadId(), completion.getFileId());
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        return completion;
    }
}
