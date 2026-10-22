package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.binlibrary.request.StartBinLibraryUploadRequestBody;
import com.paytech.operations.console.bff.service.model.binlibrary.response.BinLibraryUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;

/**
 * BIN 库 CSV 经 Platform 内部文件接口直传对象存储的本地端口
 * <li>来源系统由 X-Caller-Service（operations-console-bff）确定，上传原因固定 CARD_BIN_LIBRARY_IMPORT、声明类型固定 text/csv，
 * 上传人为当前会话账号；文件策略（类型、50 MiB 上限、扫描）归 Platform 写入路由与扫描策略 CARD_BIN_CSV。</li>
 *
 * @author Claude
 */
public interface BinLibraryFileClient {

    /**
     * 开始上传，返回浏览器直传的临时 PUT 授权
     *
     * @param context 操作人
     * @param request 文件名与字节数
     * @return Platform 上传 ID、PUT 授权与到期时间（临时授权不进入日志）
     */
    FileUploadResponseBody startUpload(OperatorContext context, StartBinLibraryUploadRequestBody request);

    /**
     * 完成上传并读取登记后的扫描状态
     *
     * @param uploadId 上传 ID
     * @return 文件 ID、文件地址、扫描状态与登记时间
     */
    BinLibraryUploadCompletionResponseBody completeUpload(String uploadId);
}
