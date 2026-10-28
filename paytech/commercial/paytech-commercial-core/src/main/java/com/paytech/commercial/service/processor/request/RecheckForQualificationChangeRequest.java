package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** compliance QualificationChanged 触发的开通项前置重查请求（只取定位受影响合同所需的字段）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecheckForQualificationChangeRequest {
    /** 角色类型代码：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER；其他值忽略 */
    @NotBlank
    private String roleType;

    /** 角色标识：CLIENT 为十进制 Client ID，OPERATING_ENTITY 为 entityNo */
    @NotBlank
    private String roleId;
}
