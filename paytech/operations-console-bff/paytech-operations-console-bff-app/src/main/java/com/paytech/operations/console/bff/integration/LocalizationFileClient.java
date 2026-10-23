package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.localization.request.StartLocalizationUploadRequestBody;
import com.paytech.operations.console.bff.service.model.localization.response.LocalizationUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;

/**
 * 多语言导入文件经 Platform 内部文件接口直传对象存储的本地端口（文件上传原则 P1）
 * <li>来源系统由 X-Caller-Service（operations-console-bff）确定；原因码按用途固定为 I18N_KEY_IMPORT 或
 * I18N_TRANSLATION_IMPORT，声明类型固定 application/json，上传人为当前会话账号；文件策略归 Platform 写入路由与扫描策略。</li>
 *
 * @author Claude
 */
public interface LocalizationFileClient {

    /**
     * 开始上传，返回浏览器直传的临时 PUT 授权
     *
     * @param context 操作人
     * @param request 用途、文件名与字节数
     * @return Platform 上传 ID、PUT 授权与到期时间（临时授权不进入日志）
     */
    FileUploadResponseBody startUpload(OperatorContext context, StartLocalizationUploadRequestBody request);

    /**
     * 完成上传并读取登记后的扫描状态
     *
     * @param uploadId 上传 ID
     * @return 文件 ID、文件地址、扫描状态与登记时间
     */
    LocalizationUploadCompletionResponseBody completeUpload(String uploadId);
}
