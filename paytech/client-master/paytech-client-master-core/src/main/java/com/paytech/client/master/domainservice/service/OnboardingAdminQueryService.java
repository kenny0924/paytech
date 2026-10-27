package com.paytech.client.master.domainservice.service;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CatalogItem;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import com.paytech.client.master.domain.onboardingadmin.ClientAdminView;
import com.paytech.client.master.domain.onboardingadmin.JurisdictionAdminDetail;
import com.paytech.client.master.domain.onboardingadmin.SchemaAdminDetail;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** 运营入驻配置、Client 与入驻码的只读查询；只读本库，不开事务。 */
public interface OnboardingAdminQueryService {
    /** 全部辖区包及其规则与目录。 */
    List<JurisdictionAdminDetail> jurisdictions();

    /** 辖区全部 Schema 版本及字段。 */
    List<SchemaAdminDetail> schemas(String jurisdictionCode);

    /** 某通用目录的全部项。 */
    List<CatalogItem> catalogItems(String catalogCode);

    /** Client 分页；筛选为空表示不限。 */
    PagingResponseBody<ClientAdminView> clients(String status, String jurisdictionCode, Long responsiblePartnerId,
        PagingRequestBody paging);

    /** Client 列表行；不存在抛出 CLIENT_NOT_FOUND。 */
    ClientAdminView client(long clientId);

    /** Client 的全部申请，新在前。 */
    List<ClientApplicationSubmission> applications(long clientId);

    /** Local Partner 的入驻码；Partner 不存在抛出 LOCAL_PARTNER_NOT_FOUND。 */
    List<PartnerOnboardingCode> onboardingCodes(long localPartnerId);
}
