package com.paytech.client.master.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.entityadmin.ClientMasterEntityAdminApi;
import com.paytech.client.master.api.entityadmin.request.ChangeAcquiringQualificationRequestBody;
import com.paytech.client.master.api.entityadmin.request.ReplacePartnerRolesRequestBody;
import com.paytech.client.master.api.entityadmin.response.ClientSummaryResponseBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerSummaryResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntityDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntitySummaryResponseBody;
import com.paytech.client.master.domainservice.service.EntityAdminQueryService;
import com.paytech.client.master.service.processor.ChangeAcquiringQualificationProcessor;
import com.paytech.client.master.service.processor.ReplacePartnerRolesProcessor;
import com.paytech.client.master.service.processor.request.ChangeAcquiringQualificationRequest;
import com.paytech.client.master.service.processor.request.ReplacePartnerRolesRequest;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * OE 与 Local Partner 运营维护的私网协议适配；操作者来自 Gateway 可信头，只校验会话存在并记录
 * <li>与 {@link com.paytech.client.master.configuration.EntityAdminConfiguration} 同条件注册。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(prefix = "onboarding", name = {"draft-enabled", "recovery-enabled"}, havingValue = "true")
public class ClientMasterEntityAdminController implements ClientMasterEntityAdminApi {
    private final EntityAdminQueryService queries;
    private final ReplacePartnerRolesProcessor partnerRoles;
    private final ChangeAcquiringQualificationProcessor qualifications;

    /** 经营主体分页。 */
    @Override
    public PagingResponseBody<OperatingEntitySummaryResponseBody> listOperatingEntities(String actorAccountId, String sessionId,
        String status, String sourceType, String country, Integer page, Integer size) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return queries.operatingEntities(EntityAdminConverter.entityStatus(status), EntityAdminConverter.sourceType(sourceType),
            EntityAdminConverter.country(country), EntityAdminConverter.paging(page, size)).map(EntityAdminConverter::entitySummary);
    }

    /** 经营主体详情。 */
    @Override
    public OperatingEntityDetailResponseBody getOperatingEntity(String actorAccountId, String sessionId, String entityNo) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return EntityAdminConverter.entityDetail(queries.operatingEntity(EntityAdminConverter.entityNo(entityNo)));
    }

    /** 修改收单资质。 */
    @Override
    public OperatingEntityDetailResponseBody changeAcquiringQualification(String actorAccountId, String sessionId,
        String entityNo, ChangeAcquiringQualificationRequestBody request) {
        long actor = ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return EntityAdminConverter.entityDetail(qualifications.process(new ChangeAcquiringQualificationRequest(
            EntityAdminConverter.entityNo(entityNo), request.getAcquiringQualification(), request.getExpectedChangeVersion(),
            request.getReason(), actor)));
    }

    /** Local Partner 分页。 */
    @Override
    public PagingResponseBody<LocalPartnerSummaryResponseBody> listLocalPartners(String actorAccountId, String sessionId,
        String status, String role, Integer page, Integer size) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return queries.localPartners(EntityAdminConverter.partnerStatus(status), EntityAdminConverter.role(role),
            EntityAdminConverter.paging(page, size)).map(EntityAdminConverter::partnerSummary);
    }

    /** Local Partner 详情。 */
    @Override
    public LocalPartnerDetailResponseBody getLocalPartner(String actorAccountId, String sessionId, String localPartnerId) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return EntityAdminConverter.partnerDetail(queries.localPartner(EntityAdminConverter.localPartnerId(localPartnerId)));
    }

    /** 整体替换角色。 */
    @Override
    public LocalPartnerDetailResponseBody replacePartnerRoles(String actorAccountId, String sessionId, String localPartnerId,
        ReplacePartnerRolesRequestBody request) {
        long actor = ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return EntityAdminConverter.partnerDetail(partnerRoles.process(new ReplacePartnerRolesRequest(
            EntityAdminConverter.localPartnerId(localPartnerId), request.getRoles(), request.getExpectedChangeVersion(),
            request.getReason(), actor)));
    }

    /** Client 主体摘要。 */
    @Override
    public ClientSummaryResponseBody getClientSummary(String actorAccountId, String sessionId, String clientId) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return EntityAdminConverter.clientSummary(queries.clientSummary(EntityAdminConverter.clientId(clientId)));
    }
}
