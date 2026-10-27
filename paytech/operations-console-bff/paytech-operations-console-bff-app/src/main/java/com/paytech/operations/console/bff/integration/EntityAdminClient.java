package com.paytech.operations.console.bff.integration;

import com.paytech.client.master.api.entityadmin.request.ChangeAcquiringQualificationRequestBody;
import com.paytech.client.master.api.entityadmin.request.ReplacePartnerRolesRequestBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerSummaryResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntityDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntitySummaryResponseBody;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.clientreview.response.ResponsiblePartnerListResponseBody;
import com.paytech.operations.console.bff.service.model.qualificationadmin.response.QualificationSubjectResponseBody;

/** client-master 主档维护：经营主体、Local Partner 与资格登记主体核对；单次调用，不自动重试。 */
public interface EntityAdminClient {
    /** B1 经营主体分页。 */
    PagingResponseBody<OperatingEntitySummaryResponseBody> listOperatingEntities(OperatorContext operator, String status,
        String sourceType, String country, Integer page, Integer size);

    /** B2 经营主体详情。 */
    OperatingEntityDetailResponseBody operatingEntity(OperatorContext operator, String entityNo);

    /** B3 修改收单资质。 */
    OperatingEntityDetailResponseBody changeAcquiringQualification(OperatorContext operator, String entityNo,
        ChangeAcquiringQualificationRequestBody request);

    /** B4 Local Partner 分页。 */
    PagingResponseBody<LocalPartnerSummaryResponseBody> listLocalPartners(OperatorContext operator, String status, String role,
        Integer page, Integer size);

    /** B5 Local Partner 详情。 */
    LocalPartnerDetailResponseBody localPartner(OperatorContext operator, String localPartnerId);

    /**
     * 可作为责任方的 PayFac 选项：ACTIVE 且有 OE_PROVIDER 角色的 Local Partner
     *
     * @param operator 当前操作人
     * @return 前 100 个选项，按 localPartnerId 升序
     */
    ResponsiblePartnerListResponseBody responsiblePartners(OperatorContext operator);

    /** B6 整体替换 Partner 角色。 */
    LocalPartnerDetailResponseBody replacePartnerRoles(OperatorContext operator, String localPartnerId,
        ReplacePartnerRolesRequestBody request);

    /**
     * B7 / B10 / B8 资格主体核对：按角色读取 OE 详情、Partner 详情或 Client 摘要
     *
     * @param operator 当前操作人
     * @param roleType 资格角色类型
     * @param roleId OE 为 entityNo，其他为十进制 ID
     * @return 角色标识、展示名称与主体状态
     *         <li>主体不存在或标识格式不符：client-master 的 404 原样抛出。</li>
     */
    QualificationSubjectResponseBody subject(OperatorContext operator, QualificationRoleType roleType, String roleId);
}
