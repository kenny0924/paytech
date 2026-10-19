package com.paytech.commercial.domainservice.service.impl;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.commercial.api.enums.CommercialPriceErrorCode;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialPriceCandidate;
import com.paytech.commercial.domain.CommercialPriceQuery;
import com.paytech.commercial.domain.CommercialPriceView;
import com.paytech.commercial.domain.PricingFacts;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.service.CommercialPriceResolutionService;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** CommercialPriceResolutionService 实现：只读 mor_commercial。 */
@Service
@AllArgsConstructor
public class CommercialPriceResolutionServiceImpl implements CommercialPriceResolutionService {
    private final CommercialPriceViewReader reader;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;

    /** 版本 → 父链与适用关系 → 同 OE 同币种候选 → 命中算法 → 组件与档位。 */
    @Override
    public CommercialPriceView resolve(CommercialPriceQuery query, PricingFacts facts) {
        CommercialTermVersion version = versionOrNotFound(query.getTermVersionId());
        CommercialTermPlan plan = commercialTermPlanRepository.findById(version.getTermPlanId());
        checkParentChain(query, plan, version);
        List<CommercialTermPrice> prices = commercialTermPriceRepository.findByVersionId(version.getTermVersionId()).stream()
            .filter(price -> price.getEntityNo().equals(query.getEntityNo()) && price.getCurrency().equals(query.getCurrency()))
            .toList();
        CommercialPriceCandidate hit = CommercialPriceCandidate.selectHit(reader.candidates(prices), facts);
        return reader.views(plan, version, List.of(hit)).getFirst();
    }

    /** 价格 → 版本 → 方案，不重新选价。 */
    @Override
    public CommercialPriceView getPrice(long termPriceId) {
        CommercialTermPrice price = commercialTermPriceRepository.findById(termPriceId);
        if (price == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "价格不存在: " + termPriceId);
        }
        CommercialTermVersion version = commercialTermVersionRepository.findById(price.getTermVersionId());
        CommercialTermPlan plan = commercialTermPlanRepository.findById(version.getTermPlanId());
        return reader.views(plan, version, reader.candidates(List.of(price))).getFirst();
    }

    /** 版本 → 方案条款类型核对 → 该 OE（及币种）的全部价格。 */
    @Override
    public List<CommercialPriceView> listVersionPrices(long termVersionId, String entityNo, String termType, String currency) {
        CommercialTermVersion version = versionOrNotFound(termVersionId);
        CommercialTermPlan plan = commercialTermPlanRepository.findById(version.getTermPlanId());
        if (!plan.getTermType().equals(termType)) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "该条款类型下版本不存在: " + termVersionId + ", " + termType);
        }
        List<CommercialTermPrice> prices = commercialTermPriceRepository.findByVersionId(termVersionId).stream()
            .filter(price -> price.getEntityNo().equals(entityNo) && (currency == null || price.getCurrency().equals(currency)))
            .sorted(Comparator.comparing(CommercialTermPrice::getCurrency).thenComparing(CommercialTermPrice::getPriorityNo))
            .toList();
        return reader.views(plan, version, reader.candidates(prices));
    }

    /**
     * 父链与适用关系：合同、条款类型、Owner、OE 覆盖逐值一致，且版本是商业时点的方案适用版本（§2.3）；不符为 409 PRICE_AMBIGUOUS。
     */
    void checkParentChain(CommercialPriceQuery query, CommercialTermPlan plan, CommercialTermVersion version) {
        boolean owner = plan.getClientId() != null
            ? plan.getClientId().equals(query.getClientId()) && query.getLocalPartnerId() == null
            : plan.getLocalPartnerId().equals(query.getLocalPartnerId()) && query.getClientId() == null;
        boolean entity = plan.getEntityNo() != null ? plan.getEntityNo().equals(query.getEntityNo())
            : agreementOperatingEntityRepository.findByUniqueKey(version.getAgreementId(), query.getEntityNo()) != null;
        CommercialTermVersion effective =
            commercialTermVersionRepository.findEffectiveAt(plan.getTermPlanId(), query.getContextAsOfTime());
        boolean applicable = effective != null && effective.getTermVersionId().equals(version.getTermVersionId());
        if (version.getAgreementId() != query.getAgreementId() || !plan.getTermType().equals(query.getTermType()) || !owner
            || !entity || !applicable) {
            throw new FailException(CommercialPriceErrorCode.PRICE_AMBIGUOUS, "价格父链冲突: 版本 " + version.getTermVersionId()
                + ", 合同 " + query.getAgreementId() + ", 条款类型 " + query.getTermType() + ", OE " + query.getEntityNo()
                + ", 时点适用 " + applicable);
        }
    }

    /** 版本，不存在为 404。 */
    CommercialTermVersion versionOrNotFound(long termVersionId) {
        CommercialTermVersion version = commercialTermVersionRepository.findById(termVersionId);
        if (version == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "商业条款版本不存在: " + termVersionId);
        }
        return version;
    }
}
