package com.paytech.commercial.domainservice.service.impl;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.OWNER_UNAVAILABLE;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.Agreement;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.PricingProfile;
import com.paytech.commercial.domain.CommercialCheckoutFacts;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domainservice.repository.*;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 在一个 REPEATABLE READ 只读事务中读取全部商务事实（design/06 §2.2 第 2 步）；不持有数据库行锁。
 * <li>快照保护合同、参与方、订阅与开通项、条款版本、价格与合同根来自同一提交时点，混合时点会让签署、开通与定价校验或复核比对失真。</li>
 */
@AllArgsConstructor
public class CheckoutQueryServiceImpl implements CheckoutQueryService {
    private final TransactionTemplate reads;

    private final AgreementRepository agreementRepository;
    private final AgreementPartyRepository agreementPartyRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final AgreementDocumentRefRepository agreementDocumentRefRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final PricingProfileRepository pricingProfileRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;
    private final AgreementProductRepository agreementProductRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final AgreementCapabilityLimitRepository agreementCapabilityLimitRepository;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final PricingProfileCondRepository pricingProfileCondRepository;

    /** 数据库故障保持不可用语义；业务冲突在内存判断阶段产生，不在这里转换。 */
    @Override
    public CommercialCheckoutFacts load(CommercialCheckoutScope scope) {
        try {
            return reads.execute(status -> loadInSnapshot(scope));
        } catch (DataAccessException | TransactionException exception) {
            throw new ErrorException(OWNER_UNAVAILABLE, "commercial 数据读取不可用", exception);
        }
    }

    /** 只读事务内的全部查询；订阅与开通项按范围内合同读取，开通项目录按实际引用的代码读取。 */
    private CommercialCheckoutFacts loadInSnapshot(CommercialCheckoutScope scope) {
        List<Agreement> agreements = agreementRepository.findForCheckout(scope);
        List<Long> agreementIds = agreements.stream().map(Agreement::agreementId).toList();
        List<AgreementCapability> capabilities = agreementCapabilityRepository.findByAgreementIds(agreementIds);
        List<PricingProfile> profiles = pricingProfileRepository.findForCheckout(scope);
        return new CommercialCheckoutFacts(agreements, agreementOperatingEntityRepository.findForCheckout(scope),
            agreementPartyRepository.findForCheckout(scope), agreementDocumentRefRepository.findForCheckout(scope),
            commercialTermPlanRepository.findForCheckout(scope), commercialTermVersionRepository.findForCheckout(scope),
            commercialTermPriceRepository.findForCheckout(scope), commercialTermComponentRepository.findForCheckout(scope),
            commercialTermComponentBandRepository.findForCheckout(scope), profiles,
            businessChangeRecordRepository.findCheckoutRoots(scope),
            agreementProductRepository.findByAgreementIds(agreementIds), capabilities,
            agreementCapabilityLimitRepository.findByAgreementCapabilityIds(
                capabilities.stream().map(AgreementCapability::getAgreementCapabilityId).toList()),
            productCapabilityRepository.findByCodes(
                capabilities.stream().map(AgreementCapability::getCapabilityCode).distinct().toList()),
            pricingProfileCondRepository.findByProfileIds(profiles.stream().map(PricingProfile::pricingProfileId).toList()));
    }
}
