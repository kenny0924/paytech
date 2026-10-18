package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 审核材料下载的内部请求。 */
@Getter
@AllArgsConstructor
public class ClientReviewFileDownloadRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * 审核任务 ID
     * <li>入口已按正整数格式校验。</li>
     */
    @NotBlank(message = "审核任务 ID 不能为空")
    private final String caseId;

    /**
     * 文件 ID
     * <li>必须出现在该审核任务的证据中，否则按文件不存在处理。</li>
     */
    @NotBlank(message = "文件 ID 不能为空")
    private final String fileId;
}
