package com.paytech.platform.support.ext.rest.files;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.service.processor.files.AuthorizeFileDownloadProcessor;
import com.paytech.platform.support.service.processor.files.CompleteFileUploadProcessor;
import com.paytech.platform.support.service.processor.files.StartFileUploadProcessor;

import lombok.AllArgsConstructor;

/** 内部文件接口的协议适配；写入委托 Processor，元数据查询直接委托文件 Service。只记录元数据日志。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class InternalFileController implements InternalFileApi {

    private final FileLifecycleService files;
    private final InternalFileConverter converter;
    private final StartFileUploadProcessor startProcessor;
    private final CompleteFileUploadProcessor completeProcessor;
    private final AuthorizeFileDownloadProcessor downloadProcessor;

    /** 新建上传返回 201。 */
    @Override
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponseBody startUpload(StartFileUploadRequestBody requestBody) {
        return converter.upload(startProcessor.process(converter.start(requestBody)));
    }

    /** 完成上传；重复完成返回原回执。 */
    @Override
    public FileUploadCompletionResponseBody completeUpload(String uploadId) {
        return converter.completion(completeProcessor.process(converter.complete(uploadId)));
    }

    /** 按文件 ID 查询元数据；不做文件级权限。 */
    @Override
    public StoredFileResponseBody getFile(String fileId) {
        converter.callerService();
        return converter.file(files.describe(converter.id(fileId, "文件 ID 必须为规范正整数")));
    }

    /** 签发短时下载地址；不做文件级权限。 */
    @Override
    public FileDownloadResponseBody authorizeDownload(String fileId) {
        return converter.downloadResult(downloadProcessor.process(converter.downloadRequest(fileId)));
    }
}
