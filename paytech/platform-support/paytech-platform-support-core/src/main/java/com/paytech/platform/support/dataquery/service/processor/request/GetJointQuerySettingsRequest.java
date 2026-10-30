package com.paytech.platform.support.dataquery.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 联合查询页初始化数据的内部请求；调用方已由 Controller 解析。 */
@Getter
@AllArgsConstructor
public class GetJointQuerySettingsRequest {

    /** 调用方服务名（X-Caller-Service，携带内部 Token 时为 Token sub）。 */
    private final String callerService;

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;
}
