package com.paytech.operations.console.bff.integration;

import java.time.Instant;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.fileupload.request.StartPublicFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadConstraintsResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

/**
 * 文件上传页经 Platform 内部文件接口上传公开文件的本地端口
 * <li>来源系统由 X-Caller-Service（operations-console-bff）确定；上传原因固定 OPERATIONS_PUBLIC_ASSET、访问级别固定 PUBLIC，
 * 上传人为当前会话账号；写入规则、类型与大小上限归 Platform。</li>
 *
 * @author Claude
 */
public interface PlatformPublicFileClient {

    /** 本入口上传的来源系统（Platform 按内部调用身份登记）。 */
    String SOURCE_SYSTEM = "operations-console-bff";

    /** 本入口上传的原因代码。 */
    String REASON_CODE = "OPERATIONS_PUBLIC_ASSET";

    /** 本入口上传的访问级别。 */
    String ACCESS_LEVEL = "PUBLIC";

    /**
     * 本 BFF 公开上传当前生效的写入限制
     *
     * @return 是否已配置、目标存储、检查策略、允许类型、大小上限与上传期限
     */
    FileUploadConstraintsResponseBody constraints();

    /**
     * 开始上传，返回浏览器直传的临时 PUT 授权
     *
     * @param context 操作人
     * @param request 文件名、声明类型与字节数
     * @return Platform 上传 ID、PUT 授权与到期时间（临时授权不进入日志）
     */
    FileUploadResponseBody startUpload(OperatorContext context, StartPublicFileUploadRequestBody request);

    /**
     * 完成上传
     *
     * @param uploadId 上传 ID
     * @return Platform 完成回执：文件 ID、文件地址、登记时间、版本号与访问级别
     */
    FileUploadCompletionResponseBody completeUpload(String uploadId);

    /**
     * 按文件 ID 查询元数据；不核对来源，由调用方判断是否属于本入口
     *
     * @param fileId 文件 ID
     * @return 元数据、扫描状态与公开地址
     */
    StoredFileResponseBody file(String fileId);

    /**
     * 分页查询本入口的公开上传（Platform 限定为本 BFF 来源，并固定原因与访问级别）
     *
     * @param createdFrom 登记时间起点（含）
     * @param createdTo 登记时间终点（不含）
     * @param uploaderAccountId 可选上传人账号；为空不限制
     * @param page 页码
     * @param size 每页条数
     * @return Platform 分页结果
     */
    PagingResponseBody<StoredFileResponseBody> files(Instant createdFrom, Instant createdTo, String uploaderAccountId,
        int page, int size);
}
