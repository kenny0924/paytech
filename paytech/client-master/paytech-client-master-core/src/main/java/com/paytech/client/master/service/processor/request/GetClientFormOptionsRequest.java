package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** C03 表单目录的内部请求。 */
@Getter
@AllArgsConstructor
public class GetClientFormOptionsRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
    /** 客户类型名称；未知或为空时按参数错误拒绝。 */
    private final String clientType;
}
