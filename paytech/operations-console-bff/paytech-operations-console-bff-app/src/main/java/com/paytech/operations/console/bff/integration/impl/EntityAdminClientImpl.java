package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.ClientReviewRiskClientImpl.require;

import org.springframework.stereotype.Component;

import com.paytech.client.master.api.entityadmin.ClientMasterEntityAdminApi;
import com.paytech.client.master.api.entityadmin.request.ChangeAcquiringQualificationRequestBody;
import com.paytech.client.master.api.entityadmin.request.ReplacePartnerRolesRequestBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerSummaryResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntityDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntitySummaryResponseBody;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.qualificationadmin.response.QualificationSubjectResponseBody;

import lombok.AllArgsConstructor;

/** 调用 client-master 主档维护合同；详情与写入结果必须对应请求的主体，否则视为上游响应无效。 */
@Component
@AllArgsConstructor
public class EntityAdminClientImpl implements EntityAdminClient {
    private final ClientMasterEntityAdminApi api;

    @Override
    public PagingResponseBody<OperatingEntitySummaryResponseBody> listOperatingEntities(OperatorContext o, String status,
        String sourceType, String country, Integer page, Integer size) {
        var response = api.listOperatingEntities(o.accountId(), o.sessionId(), status, sourceType, country, page, size);
        require(response != null && response.items() != null);
        return response;
    }

    @Override
    public OperatingEntityDetailResponseBody operatingEntity(OperatorContext o, String entityNo) {
        return entity(entityNo, api.getOperatingEntity(o.accountId(), o.sessionId(), entityNo));
    }

    @Override
    public OperatingEntityDetailResponseBody changeAcquiringQualification(OperatorContext o, String entityNo,
        ChangeAcquiringQualificationRequestBody request) {
        return entity(entityNo, api.changeAcquiringQualification(o.accountId(), o.sessionId(), entityNo, request));
    }

    @Override
    public PagingResponseBody<LocalPartnerSummaryResponseBody> listLocalPartners(OperatorContext o, String status, String role,
        Integer page, Integer size) {
        var response = api.listLocalPartners(o.accountId(), o.sessionId(), status, role, page, size);
        require(response != null && response.items() != null);
        return response;
    }

    @Override
    public LocalPartnerDetailResponseBody localPartner(OperatorContext o, String localPartnerId) {
        return partner(localPartnerId, api.getLocalPartner(o.accountId(), o.sessionId(), localPartnerId));
    }

    @Override
    public LocalPartnerDetailResponseBody replacePartnerRoles(OperatorContext o, String localPartnerId,
        ReplacePartnerRolesRequestBody request) {
        return partner(localPartnerId, api.replacePartnerRoles(o.accountId(), o.sessionId(), localPartnerId, request));
    }

    @Override
    public QualificationSubjectResponseBody subject(OperatorContext o, QualificationRoleType roleType, String roleId) {
        return switch (roleType) {
            case OPERATING_ENTITY -> {
                var entity = operatingEntity(o, roleId);
                yield new QualificationSubjectResponseBody(roleType.getCode(), entity.getEntityNo(),
                    displayName(entity.getLegalName(), entity.getEntityNo()), entity.getStatus());
            }
            case LOCAL_PARTNER -> {
                var partner = localPartner(o, roleId);
                yield new QualificationSubjectResponseBody(roleType.getCode(), partner.getLocalPartnerId(),
                    displayName(partner.getLegalName(), partner.getPartnerNo()), partner.getStatus());
            }
            case CLIENT -> {
                var client = api.getClientSummary(o.accountId(), o.sessionId(), roleId);
                require(client != null && roleId.equals(client.getClientId()) && client.getStatus() != null);
                yield new QualificationSubjectResponseBody(roleType.getCode(), client.getClientId(),
                    displayName(client.getLegalName(), client.getClientNo()), client.getStatus());
            }
        };
    }

    /** 法定名称缺失时以业务编号展示。 */
    static String displayName(String legalName, String number) {
        return legalName == null || legalName.isBlank() ? number : legalName;
    }

    /** 经营主体结果必须属于本 OE。 */
    static OperatingEntityDetailResponseBody entity(String entityNo, OperatingEntityDetailResponseBody response) {
        require(response != null && entityNo.equals(response.getEntityNo()) && response.getStatus() != null);
        return response;
    }

    /** Partner 结果必须属于本 Partner。 */
    static LocalPartnerDetailResponseBody partner(String localPartnerId, LocalPartnerDetailResponseBody response) {
        require(response != null && localPartnerId.equals(response.getLocalPartnerId()) && response.getStatus() != null);
        return response;
    }
}
