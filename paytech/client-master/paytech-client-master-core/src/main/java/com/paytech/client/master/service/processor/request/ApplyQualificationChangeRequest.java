package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** compliance QualificationChanged 事件解析后的内部请求。 */
@Getter
@AllArgsConstructor
public class ApplyQualificationChangeRequest {
    /** 角色类型：CLIENT、OPERATING_ENTITY 或 LOCAL_PARTNER。 */
    @NotBlank
    @Size(max = 64)
    private final String roleType;
    /** 角色 ID（CLIENT 为 client_id 十进制）。 */
    @NotBlank
    @Size(max = 64)
    private final String roleId;
    /** 迁移后资格状态。 */
    @NotBlank
    @Size(max = 32)
    private final String status;
    /** 引起迁移的资格决定 ID。 */
    private final Long decisionId;
}
