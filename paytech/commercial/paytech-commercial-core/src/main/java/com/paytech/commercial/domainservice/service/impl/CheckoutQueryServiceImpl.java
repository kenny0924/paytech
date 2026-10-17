package com.paytech.commercial.domainservice.service.impl;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.OWNER_UNAVAILABLE;

import java.time.Clock;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.domain.CheckoutCandidateContext;
import com.paytech.commercial.domain.CheckoutQuery;
import com.paytech.commercial.domain.CheckoutResolutionContext;
import com.paytech.commercial.domainservice.repository.*;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 编排本地多表一致读取和跨 Owner 调用后的复核；不持有数据库行锁。 */
@AllArgsConstructor
public class CheckoutQueryServiceImpl implements CheckoutQueryService {
    private final Clock clock;
    private final TransactionTemplate reads;

    private final ClientRepository clientRepository;
    private final AgreementRepository agreementRepository;
    private final LocalPartnerRepository localPartnerRepository;
    private final PartyProfileRepository partyProfileRepository;
    private final AgreementPartyRepository agreementPartyRepository;
    private final OperatingEntityRepository operatingEntityRepository;
    private final SellerDisclosureRepository sellerDisclosureRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final AgreementDocumentRefRepository agreementDocumentRefRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final EntitySourceRelationRepository entitySourceRelationRepository;
    private final OperatingEntityScopeRepository operatingEntityScopeRepository;
    private final PaymentMethodProfileRepository paymentMethodProfileRepository;
    private final ClientBusinessProfileRepository clientBusinessProfileRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final ConsumerLegalDocumentRepository consumerLegalDocumentRepository;
    private final ClientEntityAffiliationRepository clientEntityAffiliationRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;
    private final OperatingEntityApplicationRepository operatingEntityApplicationRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;
    private final ClientBusinessClassificationRepository clientBusinessClassificationRepository;

    /** 在一个只读事务取得完整本地 Context，不执行外部资格调用。 */
    @Override
    public CheckoutResolutionContext load(CheckoutQuery query) {
        return read(() -> loadContext(query));
    }

    /** 外部检查结束后取得新快照，复核原选择及相关 Root 在当前时点的准入。 */
    @Override
    public void verify(CheckoutResolutionContext context, CheckoutCandidateContext candidate) {
        read(() -> {
            var current = loadContext(context.query());
            context.verifyUnchanged(current, candidate, clock.instant());
            return null;
        });
    }

    /** 完整解析与复核共用同一组事实；所属事务由公开方法统一开启。 */
    private CheckoutResolutionContext loadContext(CheckoutQuery query) {
        long clientId = query.clientId();
        return CheckoutResolutionContext.builder().query(query).client(clientRepository.findById(clientId))
            .businessProfiles(clientBusinessProfileRepository.findByClientId(clientId))
            .classifications(clientBusinessClassificationRepository.findForCheckout(clientId))
            .affiliations(clientEntityAffiliationRepository.findByClientId(clientId))
            .entities(operatingEntityRepository.findForCheckout(clientId))
            .sources(entitySourceRelationRepository.findForCheckout(clientId))
            .scopes(operatingEntityScopeRepository.findForCheckout(clientId))
            .scopeApplications(operatingEntityApplicationRepository.findForCheckout(clientId))
            .partners(localPartnerRepository.findForCheckout(clientId))
            .agreements(agreementRepository.findForCheckout(clientId))
            .agreementEntities(agreementOperatingEntityRepository.findForCheckout(clientId))
            .agreementParties(agreementPartyRepository.findForCheckout(clientId))
            .agreementDocuments(agreementDocumentRefRepository.findForCheckout(clientId))
            .partyProfiles(partyProfileRepository.findForCheckout(clientId))
            .termPlans(commercialTermPlanRepository.findForCheckout(clientId))
            .termVersions(commercialTermVersionRepository.findForCheckout(clientId))
            .termPrices(commercialTermPriceRepository.findForCheckout(clientId))
            .termComponents(commercialTermComponentRepository.findForCheckout(clientId))
            .termBands(commercialTermComponentBandRepository.findForCheckout(clientId))
            .methodProfiles(paymentMethodProfileRepository.findForCheckout(clientId))
            .legalDocuments(consumerLegalDocumentRepository.findForCheckout(clientId))
            .sellerDisclosures(sellerDisclosureRepository.findForCheckout(clientId))
            .roots(businessChangeRecordRepository.findCheckoutRoots(clientId)).build();
    }

    /** 数据库故障保持不可用语义；业务冲突原样传播，不转成候选落选。 */
    private <T> T read(Supplier<T> action) {
        try {
            return reads.execute(status -> action.get());
        } catch (DataAccessException | TransactionException exception) {
            throw new ErrorException(OWNER_UNAVAILABLE, "CCT 数据读取不可用", exception);
        }
    }
}
