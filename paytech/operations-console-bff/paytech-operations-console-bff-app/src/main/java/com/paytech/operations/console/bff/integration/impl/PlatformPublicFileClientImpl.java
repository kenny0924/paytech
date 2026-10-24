package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformPublicFileClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.fileupload.request.StartPublicFileUploadRequestBody;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadConstraintsResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

import lombok.AllArgsConstructor;

/**
 * 公开文件上传的 Platform 转发：每个方法调用一次 Platform 内部文件接口，不重试
 * <li>回执的必要字段缺失或互不一致按上游无效响应（502）处理；完成回执沿用 BIN 库上传的上传 ID、文件 ID 与文件地址核对。</li>
 * <li>PUT 授权只随响应返回浏览器，不进入异常消息或日志。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PlatformPublicFileClientImpl implements PlatformPublicFileClient {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final InternalFileApi api;

    /** 公开级别的写入限制；回执须回显 PUBLIC。 */
    @Override
    public FileUploadConstraintsResponseBody constraints() {
        var constraints = api.getUploadConstraints(ACCESS_LEVEL);
        require(constraints != null && ACCESS_LEVEL.equals(constraints.getAccessLevel()));
        return constraints;
    }

    /** 开始公开上传；回执须带上传 ID 与 PUT 授权。 */
    @Override
    public FileUploadResponseBody startUpload(OperatorContext context, StartPublicFileUploadRequestBody request) {
        var upload = api.startUpload(new StartFileUploadRequestBody(REASON_CODE, request.getFilename(),
            request.getMimeType(), request.getSizeBytes(), context.accountId(), ACCESS_LEVEL));
        require(upload != null && upload.getUploadId() != null && upload.getUploadId().matches(ID)
            && upload.getAuthorization() != null && upload.getAuthorization().getUrl() != null);
        return upload;
    }

    /** 完成上传；回执的上传 ID、文件 ID 与文件地址必须自洽。 */
    @Override
    public FileUploadCompletionResponseBody completeUpload(String uploadId) {
        var completion = api.completeUpload(uploadId);
        require(completion != null && Objects.equals(uploadId, completion.getUploadId()) && completion.getFileId() != null
            && completion.getFileId().matches(ID)
            && BinLibraryFileClientImpl.addresses(completion.getFileAddress(), completion.getFileId())
            && completion.getCompletedAt() != null);
        return completion;
    }

    /** 文件元数据；回执须是所查询的文件。 */
    @Override
    public StoredFileResponseBody file(String fileId) {
        var file = api.getFile(fileId);
        require(file != null && Objects.equals(fileId, file.getFileId()) && file.getScanStatus() != null);
        return file;
    }

    /** 本入口公开上传的分页；分页窗口须与请求一致。 */
    @Override
    public PagingResponseBody<StoredFileResponseBody> files(Instant createdFrom, Instant createdTo,
        String uploaderAccountId, int page, int size) {
        var files = api.listFiles(createdFrom, createdTo, REASON_CODE, ACCESS_LEVEL, uploaderAccountId, page, size);
        require(files != null && files.items() != null && files.page() == page && files.size() == size
            && files.items().stream().allMatch(item -> item != null && item.getFileId() != null));
        return files;
    }
}
