package com.paytech.client.master.domainservice.service.impl;

import static com.paytech.client.master.api.enums.CheckoutCandidateErrorCode.CANDIDATE_SOURCE_UNAVAILABLE;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.PartyProfile;
import com.paytech.client.master.domain.checkout.CandidateVerification;
import com.paytech.client.master.domain.checkout.CheckoutCandidateQuery;
import com.paytech.client.master.domain.checkout.CheckoutEligibility;
import com.paytech.client.master.domain.checkout.CheckoutEligibilityContext;
import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.service.CheckoutCandidateQueryService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 候选查询与复核各在一个 REPEATABLE READ 只读事务中读取全部主档事实（design/06 §2.1）
 * <li>快照保护候选排序、来源、Scope 审批与根版本来自同一提交时点；混合时点会让复核误判或漏判。</li>
 * <li>不持有数据库行锁；数据库故障返回 CANDIDATE_SOURCE_UNAVAILABLE，由 commercial 失败关闭。</li>
 */
@AllArgsConstructor
public class CheckoutCandidateQueryServiceImpl implements CheckoutCandidateQueryService {
    private final Clock clock;
    private final TransactionTemplate reads;

    private final ClientRepository clientRepository;
    private final PartnerRoleRepository partnerRoleRepository;
    private final LocalPartnerRepository localPartnerRepository;
    private final PartyProfileRepository partyProfileRepository;
    private final OperatingEntityRepository operatingEntityRepository;
    private final SellerDisclosureRepository sellerDisclosureRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final EntitySourceRelationRepository entitySourceRelationRepository;
    private final OperatingEntityScopeRepository operatingEntityScopeRepository;
    private final ClientBusinessProfileRepository clientBusinessProfileRepository;
    private final ConsumerLegalDocumentRepository consumerLegalDocumentRepository;
    private final ClientStoredCardSettingRepository clientStoredCardSettingRepository;
    private final ClientEntityAffiliationRepository clientEntityAffiliationRepository;
    private final OperatingEntityApplicationRepository operatingEntityApplicationRepository;
    private final ClientBusinessClassificationRepository clientBusinessClassificationRepository;

    /** 快照读取后在事务外构造候选；构造只做内存判断。 */
    @Override
    public CheckoutEligibility candidates(CheckoutCandidateQuery query) {
        return read(() -> load(query)).eligibility();
    }

    /** 新快照读取后按首次根版本、当前时点与商业时点复核。 */
    @Override
    public List<String> verify(CandidateVerification verification) {
        return read(() -> load(verification.query())).changes(verification, clock.instant());
    }

    /** 单条按主键集合的查询，不开事务。 */
    @Override
    public List<PartyProfile> partyProfiles(Collection<Long> ids) {
        try {
            return partyProfileRepository.findByIds(ids);
        } catch (DataAccessException exception) {
            throw new ErrorException(CANDIDATE_SOURCE_UNAVAILABLE, "客户主档资料读取不可用", exception);
        }
    }

    /** 候选与复核共用同一组事实；所属事务由 read 统一开启。 */
    private CheckoutEligibilityContext load(CheckoutCandidateQuery query) {
        long clientId = query.clientId();
        return CheckoutEligibilityContext.builder().query(query).client(clientRepository.findById(clientId))
            .businessProfiles(clientBusinessProfileRepository.findByClientId(clientId))
            .classifications(clientBusinessClassificationRepository.findForCheckout(clientId))
            .affiliations(clientEntityAffiliationRepository.findByClientId(clientId))
            .entities(operatingEntityRepository.findForCheckout(clientId))
            .sources(entitySourceRelationRepository.findForCheckout(clientId))
            .scopes(operatingEntityScopeRepository.findForCheckout(clientId))
            .scopeApplications(operatingEntityApplicationRepository.findForCheckout(clientId))
            .partners(localPartnerRepository.findForCheckout(clientId))
            .partnerRoles(partnerRoleRepository.findActiveForCheckout(clientId))
            .partyProfiles(partyProfileRepository.findForCheckout(clientId))
            .legalDocuments(consumerLegalDocumentRepository.findForCheckout(clientId))
            .sellerDisclosures(sellerDisclosureRepository.findForCheckout(clientId))
            .roots(businessChangeRecordRepository.findCheckoutRoots(clientId))
            .storedCardEnabled(clientStoredCardSettingRepository.isEnabled(clientId)).build();
    }

    /** 数据库故障保持不可用语义；业务冲突原样传播，不转成候选落选。 */
    private <T> T read(Supplier<T> action) {
        try {
            return reads.execute(status -> action.get());
        } catch (DataAccessException | TransactionException exception) {
            throw new ErrorException(CANDIDATE_SOURCE_UNAVAILABLE, "客户主档数据读取不可用", exception);
        }
    }
}
