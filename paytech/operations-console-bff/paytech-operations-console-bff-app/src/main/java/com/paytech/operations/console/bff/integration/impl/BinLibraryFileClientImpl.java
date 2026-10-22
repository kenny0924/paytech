package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.operations.console.bff.integration.BinLibraryFileClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.binlibrary.request.StartBinLibraryUploadRequestBody;
import com.paytech.operations.console.bff.service.model.binlibrary.response.BinLibraryUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.FileAddress;

import lombok.AllArgsConstructor;

/**
 * BIN 库 CSV 上传的 Platform 转发：开始上传一次调用；完成上传先完成、再查询一次元数据取扫描状态，均不重试
 * <li>文件地址取完成回执的 fileAddress（Platform 为 Owner）；完成回执不含扫描状态，因此再查询一次元数据。</li>
 * <li>PUT 授权只随响应返回浏览器，不进入异常消息或日志。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class BinLibraryFileClientImpl implements BinLibraryFileClient {
    /** 上传原因：Card 创建版本时按此核对文件来源。 */
    static final String REASON_CODE = "CARD_BIN_LIBRARY_IMPORT";

    /** 声明类型：Platform 完成上传时按实际字节独立识别。 */
    static final String CSV_MIME_TYPE = "text/csv";

    private static final String ID = "[1-9][0-9]{0,18}";

    private final InternalFileApi api;

    /** 开始上传；回执须带上传 ID 与 PUT 授权。 */
    @Override
    public FileUploadResponseBody startUpload(OperatorContext context, StartBinLibraryUploadRequestBody request) {
        var upload = api.startUpload(new StartFileUploadRequestBody(REASON_CODE, request.getFileName(), CSV_MIME_TYPE,
            request.getSizeBytes(), context.accountId()));
        require(upload != null && upload.getUploadId() != null && upload.getUploadId().matches(ID)
            && upload.getAuthorization() != null && upload.getAuthorization().getUrl() != null);
        return upload;
    }

    /** 完成上传后查询扫描状态；回执的上传 ID、文件 ID 与文件地址必须自洽。 */
    @Override
    public BinLibraryUploadCompletionResponseBody completeUpload(String uploadId) {
        var completion = api.completeUpload(uploadId);
        require(completion != null && Objects.equals(uploadId, completion.getUploadId()) && completion.getFileId() != null
            && completion.getFileId().matches(ID) && addresses(completion.getFileAddress(), completion.getFileId())
            && completion.getCompletedAt() != null);
        var file = api.getFile(completion.getFileId());
        require(file != null && Objects.equals(completion.getFileId(), file.getFileId()) && file.getScanStatus() != null);
        return new BinLibraryUploadCompletionResponseBody(uploadId, completion.getFileId(),
            completion.getFileAddress(), file.getScanStatus(), completion.getCompletedAt().toString());
    }

    /** 回执地址经 Platform {@link FileAddress} 解析后指向同一文件；缺失或格式不符视为无效。 */
    static boolean addresses(String fileAddress, String fileId) {
        try {
            return fileAddress != null && FileAddress.parse(fileAddress).fileId() == Long.parseLong(fileId);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
