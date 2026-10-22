package com.paytech.platform.support.api.workflow;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.workflow.request.StartWorkflowAttachmentUploadRequestBody;

import jakarta.validation.Valid;

/**
 * 工作流附件（技术方案第 5 节、第 7 节 #26—#28）：开始上传、完成上传与实例内附件的下载授权。
 * <li>均需 WORKFLOW_TASK_HANDLE。上传复用 Platform 文件入口：来源系统 platform-support、原因 WORKFLOW_ATTACHMENT、
 * 上传人为当前操作人；浏览器按返回的临时授权直传存储。附件归属不建表，由实例启动值与动作输出中保存的文件地址（/files/v1/{fileId}）表达；下载授权路径按文件 ID 寻址。</li>
 * <li>文件能力未开启时返回 503。临时上传与下载地址不进日志、不落库。</li>
 */
@HttpExchange(url = "/workflow/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface WorkflowAttachmentApi {

    /**
     * 开始上传附件
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param request 文件名、MIME 与字节数
     * @return 201；上传 ID、临时 PUT 授权与上传到期时间；类型或大小不符合扫描策略返回 422，存储不可用返回 503
     */
    @PostExchange("/attachments/uploads")
    FileUploadResponseBody startUpload(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody StartWorkflowAttachmentUploadRequestBody request);

    /**
     * 完成上传：核对存储中的对象并登记文件，随后异步扫描；只有上传人本人可以完成
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param uploadId 上传 ID
     * @return 上传 ID、文件 ID 与登记时间；重复完成返回原回执；不是本人的工作流附件上传按不存在返回 404
     */
    @PostExchange("/attachments/uploads/{uploadId}/completion")
    FileUploadCompletionResponseBody completeUpload(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("uploadId") String uploadId);

    /**
     * 签发实例附件的下载授权：请求人是实例参与人或有 WORKFLOW_INSTANCE_READ，且文件出现在本实例的启动值或动作输出中
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param instanceId 实例 ID
     * @param fileId 文件 ID
     * @return 最长 60 秒的临时下载地址；实例不可见或文件不属于本实例返回 404，文件未扫描通过返回 409
     */
    @PostExchange("/instances/{instanceId}/files/{fileId}/download")
    FileDownloadResponseBody authorizeDownload(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("instanceId") String instanceId,
        @PathVariable("fileId") String fileId);
}
