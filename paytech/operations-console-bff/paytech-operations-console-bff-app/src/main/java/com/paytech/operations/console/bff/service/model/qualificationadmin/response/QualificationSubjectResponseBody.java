package com.paytech.operations.console.bff.service.model.qualificationadmin.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** B7 资格登记的主体核对结果：把运营输入的编号解析为 compliance 角色标识与展示信息。 */
@Getter
@AllArgsConstructor
public class QualificationSubjectResponseBody {

    /** 角色类型：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER */
    private final String roleType;

    /**
     * compliance 角色标识
     * <li>OPERATING_ENTITY 为 entityNo；CLIENT、LOCAL_PARTNER 为十进制 ID。</li>
     */
    private final String roleId;

    /**
     * 展示名称
     * <li>client-master 法定名称；资料缺失时回退为 entityNo / partnerNo / clientNo。</li>
     */
    private final String displayName;

    /**
     * client-master 主体状态 Code
     * <li>OE：PENDING、ACTIVE、SUSPENDED、CLOSED；Partner 与 Client：PENDING、ACTIVE、SUSPENDED、TERMINATED。</li>
     */
    private final String status;
}
