package com.paytech.operations.console.bff.service.model.entityadmin.response;

import java.util.List;

import com.paytech.client.master.api.entityadmin.response.LocalPartnerDetailResponseBody;
import com.paytech.compliance.api.response.QualificationResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * B5 Local Partner 详情页：client-master 主档为主体，compliance 资格单独标记可用性
 * <li>qualificationsStatus 取 ReadAvailability：AVAILABLE 或 UNAVAILABLE；不可用时列表为空，页面不得推断为“未登记”。</li>
 */
@Getter
@AllArgsConstructor
public class LocalPartnerPageResponseBody {

    /** client-master Local Partner 详情，字段与 Owner 合同一致 */
    private final LocalPartnerDetailResponseBody partner;

    /** Partner（LOCAL_PARTNER + localPartnerId）资格的读取结果 */
    private final String qualificationsStatus;

    /** Partner 的全部角色资格，按 compliance 更新时间倒序 */
    private final List<QualificationResponseBody> qualifications;
}
