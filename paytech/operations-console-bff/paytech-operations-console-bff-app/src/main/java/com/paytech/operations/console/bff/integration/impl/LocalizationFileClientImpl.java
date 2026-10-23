package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.operations.console.bff.domain.enums.LocalizationUploadPurpose;
import com.paytech.operations.console.bff.integration.LocalizationFileClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.localization.request.StartLocalizationUploadRequestBody;
import com.paytech.operations.console.bff.service.model.localization.response.LocalizationUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;

import lombok.AllArgsConstructor;

/**
 * 多语言导入文件上传的 Platform 转发：开始上传一次调用；完成上传先完成、再查询一次元数据取扫描状态，均不重试
 * <li>完成回执的上传 ID、文件 ID 与文件地址必须自洽，地址由 Platform FileAddress 解析（沿用 BIN 库上传的核对）。</li>
 * <li>PUT 授权只随响应返回浏览器，不进入异常消息或日志。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class LocalizationFileClientImpl implements LocalizationFileClient {
    /** 声明类型：Platform 完成上传时按实际字节独立识别。 */
    static final String JSON_MIME_TYPE = "application/json";

    private static final String ID = "[1-9][0-9]{0,18}";

    private final InternalFileApi api;

    /** 开始上传（不声明访问级别，按私有）；回执须带上传 ID 与 PUT 授权。 */
    @Override
    public FileUploadResponseBody startUpload(OperatorContext context, StartLocalizationUploadRequestBody request) {
        LocalizationUploadPurpose purpose = EnumUtils.getEnumByCode(LocalizationUploadPurpose.class, request.getPurpose());
        var upload = api.startUpload(new StartFileUploadRequestBody(purpose.getReasonCode(), request.getFileName(),
            JSON_MIME_TYPE, request.getSizeBytes(), context.accountId(), null));
        require(upload != null && upload.getUploadId() != null && upload.getUploadId().matches(ID)
            && upload.getAuthorization() != null && upload.getAuthorization().getUrl() != null);
        return upload;
    }

    /** 完成上传后查询扫描状态。 */
    @Override
    public LocalizationUploadCompletionResponseBody completeUpload(String uploadId) {
        var completion = api.completeUpload(uploadId);
        require(completion != null && Objects.equals(uploadId, completion.getUploadId()) && completion.getFileId() != null
            && completion.getFileId().matches(ID)
            && BinLibraryFileClientImpl.addresses(completion.getFileAddress(), completion.getFileId())
            && completion.getCompletedAt() != null);
        var file = api.getFile(completion.getFileId());
        require(file != null && Objects.equals(completion.getFileId(), file.getFileId()) && file.getScanStatus() != null);
        return new LocalizationUploadCompletionResponseBody(uploadId, completion.getFileId(),
            completion.getFileAddress(), file.getScanStatus(), completion.getCompletedAt().toString());
    }
}
