package com.paytech.operations.console.bff.service.model.entityadmin.response;

import java.util.List;

import com.paytech.client.master.api.entityadmin.response.OperatingEntityDetailResponseBody;
import com.paytech.compliance.api.response.QualificationResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * B2 经营主体详情页：client-master 主档为主体，compliance 的 OE 资格与提供方资格各自标记可用性
 * <li>*Status 取 ReadAvailability：AVAILABLE、UNAVAILABLE；提供方资格在 CLIENT 来源（没有提供方）时为 NOT_APPLICABLE。</li>
 * <li>不可用或不适用时对应列表为空，页面不得推断为“未登记”。</li>
 */
@Getter
@AllArgsConstructor
public class OperatingEntityPageResponseBody {

    /** client-master 经营主体详情，字段与 Owner 合同一致 */
    private final OperatingEntityDetailResponseBody entity;

    /** OE（OPERATING_ENTITY + entityNo）资格的读取结果 */
    private final String qualificationsStatus;

    /** OE 的全部角色资格，按 compliance 更新时间倒序 */
    private final List<QualificationResponseBody> qualifications;

    /** 提供方（LOCAL_PARTNER + localPartnerId）资格的读取结果 */
    private final String providerQualificationsStatus;

    /** 提供方的全部角色资格，按 compliance 更新时间倒序 */
    private final List<QualificationResponseBody> providerQualifications;
}
