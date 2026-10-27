package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** CM-2 表单渲染规格请求；applicationId 与 jurisdiction/partyType 二选一。 */
@Getter
@AllArgsConstructor
public class GetFormSchemaRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话。 */
    @Positive
    private final long sessionId;
    /** 已创建的申请，可空。 */
    private final Long applicationId;
    /** 辖区代码，可空。 */
    private final String jurisdictionCode;
    /** 主体类型代码，可空。 */
    private final String partyType;
    /** 组织类型代码，可空。 */
    private final String organizationTypeCode;
}
