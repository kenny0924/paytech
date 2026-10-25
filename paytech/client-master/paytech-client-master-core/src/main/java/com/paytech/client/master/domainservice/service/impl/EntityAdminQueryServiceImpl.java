package com.paytech.client.master.domainservice.service.impl;

import static com.paytech.client.master.api.enums.EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND;
import static com.paytech.client.master.api.enums.EntityAdminErrorCode.OPERATING_ENTITY_NOT_FOUND;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.core.dal.dataobject.PartnerRole;
import com.paytech.client.master.domain.entityadmin.ClientSummaryView;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminDetail;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminView;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminDetail;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminView;
import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.service.EntityAdminQueryService;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 运营读取：组合 OE、来源、Partner、角色、准入范围与卖方披露；只读本库。 */
@AllArgsConstructor
public class EntityAdminQueryServiceImpl implements EntityAdminQueryService {
    private final ClientRepository clientRepository;
    private final PartnerRoleRepository partnerRoleRepository;
    private final LocalPartnerRepository localPartnerRepository;
    private final OperatingEntityRepository operatingEntityRepository;
    private final SellerDisclosureRepository sellerDisclosureRepository;
    private final EntitySourceRelationRepository entitySourceRelationRepository;
    private final OperatingEntityScopeRepository operatingEntityScopeRepository;

    /** 同一筛选条件下的 LIMIT/OFFSET 与 COUNT。 */
    @Override
    public PagingResponseBody<OperatingEntityAdminView> operatingEntities(String status, String sourceType, String country,
        PagingRequestBody paging) {
        List<OperatingEntityAdminView> items =
            operatingEntityRepository.pageForAdmin(status, sourceType, country, paging.size(), paging.offset());
        return new PagingResponseBody<>(items, paging.page(), paging.size(),
            operatingEntityRepository.countForAdmin(status, sourceType, country));
    }

    /** 组合行之外再读提供方角色、准入范围与卖方披露。 */
    @Override
    public OperatingEntityAdminDetail operatingEntity(String entityNo) {
        OperatingEntityAdminView entity = operatingEntityRepository.findAdminView(entityNo);
        if (entity == null) {
            throw new FailException(OPERATING_ENTITY_NOT_FOUND);
        }
        List<PartnerRoleCode> providerRoles = entity.getProviderPartnerNo() == null ? List.of()
            : roles(List.of(entity.getSourcePartnerId())).getOrDefault(entity.getSourcePartnerId(), List.of());
        return new OperatingEntityAdminDetail(entity, providerRoles, operatingEntityScopeRepository.findByEntityNo(entityNo),
            sellerDisclosureRepository.findByEntityNo(entityNo));
    }

    /** 当前页的角色一次批量读取后补齐。 */
    @Override
    public PagingResponseBody<LocalPartnerAdminView> localPartners(String status, String role, PagingRequestBody paging) {
        List<LocalPartnerAdminView> items = localPartnerRepository.pageForAdmin(status, role, paging.size(), paging.offset());
        Map<Long, List<PartnerRoleCode>> roles = roles(items.stream().map(LocalPartnerAdminView::getLocalPartnerId).toList());
        items.forEach(item -> item.setRoles(roles.getOrDefault(item.getLocalPartnerId(), List.of())));
        return new PagingResponseBody<>(items, paging.page(), paging.size(), localPartnerRepository.countForAdmin(status, role));
    }

    /** 组合行、角色与提供的经营主体。 */
    @Override
    public LocalPartnerAdminDetail localPartner(long localPartnerId) {
        LocalPartnerAdminView partner = localPartnerRepository.findAdminView(localPartnerId);
        if (partner == null) {
            throw new FailException(LOCAL_PARTNER_NOT_FOUND);
        }
        partner.setRoles(roles(List.of(localPartnerId)).getOrDefault(localPartnerId, List.of()));
        return new LocalPartnerAdminDetail(partner, entitySourceRelationRepository.findProvidedByPartner(localPartnerId));
    }

    /** 不存在复用 CLIENT_NOT_FOUND。 */
    @Override
    public ClientSummaryView clientSummary(long clientId) {
        ClientSummaryView summary = clientRepository.findSummary(clientId);
        if (summary == null) {
            throw new FailException(CheckoutCandidateErrorCode.CLIENT_NOT_FOUND);
        }
        return summary;
    }

    /** 一组 Partner 的 ACTIVE 角色，按 Code 升序。 */
    private Map<Long, List<PartnerRoleCode>> roles(List<Long> localPartnerIds) {
        return partnerRoleRepository.findActiveByPartners(localPartnerIds).stream()
            .collect(Collectors.groupingBy(PartnerRole::getLocalPartnerId, Collectors.mapping(PartnerRole::getRoleCode,
                Collectors.collectingAndThen(Collectors.toList(),
                    codes -> codes.stream().sorted((a, b) -> a.getCode().compareTo(b.getCode())).toList()))));
    }
}
