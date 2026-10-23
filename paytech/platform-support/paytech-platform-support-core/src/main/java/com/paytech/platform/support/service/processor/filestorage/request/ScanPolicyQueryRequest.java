package com.paytech.platform.support.service.processor.filestorage.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 检查策略列表或详情查询。 */
@Getter
@AllArgsConstructor
public class ScanPolicyQueryRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 策略代码；列表查询为空。 */
    private final String policyCode;
}
