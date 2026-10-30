package com.paytech.platform.support.dataquery.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 可达预览的内部请求；入口键类型是否可用由 Processor 按图校验（422）。 */
@Getter
@AllArgsConstructor
public class PreviewJointQueryGraphRequest {

    /** 调用方服务名（X-Caller-Service，携带内部 Token 时为 Token sub）。 */
    private final String callerService;

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 入口键类型编码，可为 null（返回 422）。 */
    private final String entryKeyType;
}
