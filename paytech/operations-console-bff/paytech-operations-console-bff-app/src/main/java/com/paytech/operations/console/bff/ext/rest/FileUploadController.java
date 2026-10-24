package com.paytech.operations.console.bff.ext.rest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformPublicFileClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.fileupload.request.StartPublicFileUploadRequestBody;
import com.paytech.operations.console.bff.service.model.fileupload.response.FileUploadRecordResponseBody;
import com.paytech.operations.console.bff.service.processor.CompleteFileUploadRecordProcessor;
import com.paytech.operations.console.bff.service.processor.GetFileUploadRecordProcessor;
import com.paytech.operations.console.bff.service.processor.ListFileUploadRecordsProcessor;
import com.paytech.operations.console.bff.service.processor.request.CompleteFileUploadRecordRequest;
import com.paytech.operations.console.bff.service.processor.request.GetFileUploadRecordRequest;
import com.paytech.operations.console.bff.service.processor.request.ListFileUploadRecordsRequest;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadConstraintsResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 文件上传页（块 136）的浏览器入口：只上传公开文件，浏览器直传对象存储
 * <li>Gateway 绑定：三个查询 FILE_UPLOAD_READ；开始上传、完成上传 FILE_UPLOAD_CREATE，并经 PLT-I02 实时授权。</li>
 * <li>写入限制与开始上传简单转发 Platform 内部文件接口；完成上传、最近上传与单个上传经 Processor 整形并核对来源与原因。</li>
 * <li>上传原因 OPERATIONS_PUBLIC_ASSET、访问级别 PUBLIC 与上传人（当前会话账号）由 BFF 固定，浏览器不能指定。</li>
 * <li>开始上传的响应含临时 PUT 授权：/file-upload/v1/uploads 只记元数据日志。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/file-upload/v1")
public class FileUploadController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final PlatformPublicFileClient client;
    private final GetFileUploadRecordProcessor getRecord;
    private final CompleteFileUploadRecordProcessor completeRecord;
    private final ListFileUploadRecordsProcessor listRecords;

    /** 公开上传当前生效的写入限制；未配置时 configured=false。 */
    @GetMapping("/constraints")
    public FileUploadConstraintsResponseBody getConstraints() {
        return client.constraints();
    }

    /** 最近上传：本入口全部人员的公开上传，mine=true 只看本人；时间范围缺省为近 7 天。 */
    @GetMapping("/files")
    public PagingResponseBody<FileUploadRecordResponseBody> listFiles(OperatorContext context,
        @RequestParam(value = "createdFrom", required = false) Instant createdFrom,
        @RequestParam(value = "createdTo", required = false) Instant createdTo,
        @RequestParam(value = "mine", required = false) Boolean mine,
        @RequestParam(value = "page", required = false) @Min(1) @Max(100000) Integer page,
        @RequestParam(value = "size", required = false) @Min(1) @Max(200) Integer size) {
        return listRecords.process(new ListFileUploadRecordsRequest(context, createdFrom, createdTo, mine, page, size,
            Instant.now().truncatedTo(ChronoUnit.MICROS)));
    }

    /** 单个上传的检查状态与地址；不是本入口上传的文件返回 404。 */
    @GetMapping("/files/{fileId}")
    public FileUploadRecordResponseBody getFile(OperatorContext context,
        @PathVariable("fileId") @Pattern(regexp = ID) String fileId) {
        return getRecord.process(new GetFileUploadRecordRequest(context, fileId));
    }

    /** 开始上传公开文件，返回 201 与浏览器直传的临时 PUT 授权。 */
    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponseBody startUpload(OperatorContext context,
        @RequestBody @NotNull @Valid StartPublicFileUploadRequestBody body) {
        return client.startUpload(context, body);
    }

    /** 完成上传，返回 Platform 完成回执（含文件 ID，供页面轮询检查状态）；完成的文件不是本入口的公开上传时 404；不接受 Body。 */
    @PostMapping("/uploads/{uploadId}/completion")
    public FileUploadCompletionResponseBody completeUpload(@PathVariable("uploadId") @Pattern(regexp = ID) String uploadId) {
        return completeRecord.process(new CompleteFileUploadRecordRequest(uploadId));
    }
}
