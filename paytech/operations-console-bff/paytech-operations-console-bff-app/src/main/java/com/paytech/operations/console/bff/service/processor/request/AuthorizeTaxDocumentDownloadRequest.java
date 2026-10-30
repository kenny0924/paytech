package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 税票下载授权的内部请求。 */
@Getter
@AllArgsConstructor
public class AuthorizeTaxDocumentDownloadRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * 公开订单号
     * <li>tax 只提供按订单读取单据，下载须在该订单的正式交易单据中找到税票。</li>
     */
    @NotBlank(message = "订单号不能为空")
    private final String orderNo;

    /**
     * 单据 ID
     * <li>入口已按正整数格式校验；不属于该订单时按不存在处理。</li>
     */
    @NotBlank(message = "单据 ID 不能为空")
    private final String documentId;
}
