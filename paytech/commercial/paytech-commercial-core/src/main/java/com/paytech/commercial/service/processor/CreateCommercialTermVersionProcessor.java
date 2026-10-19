package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.core.constant.CommercialLocks;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeHistory;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.CommercialTermRoundingMode;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.service.processor.request.CreateCommercialTermVersionRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 在方案下创建 DRAFT 版本：版本号服务端递增，可复制同方案已有版本的全部价格、组件与档位（新 ID）
 * <li>Client 条款版本冻结方案合同；Partner 条款版本冻结请求给出的同 Local Partner PARTNER 合同。</li>
 * <li>生效起点 NOT NULL，DRAFT 至 APPROVED 期间以创建时刻占位，排期时写入真实起点；舍入方式本期固定 HALF_UP。</li>
 * <li>方案锁 term-plan:{planId} 持续等待；根 CAS、版本、BCR Child、CREATE 快照与复制行同事务写入。</li>
 */
@Component
@AllArgsConstructor
public class CreateCommercialTermVersionProcessor
    extends AbstractLockProcessor<CreateCommercialTermVersionRequest, CommercialTermVersionView> {
    private final Clock clock;
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;
    private final CommercialTermVersionRepository commercialTermVersionRepository;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CreateCommercialTermVersionRequest request) {
        return LockCommand.lock(CommercialLocks.termPlan(request.getPlanId()));
    }

    @Override
    protected CommercialTermVersionView processWithinLock(CreateCommercialTermVersionRequest request) {
        // 方案、根版本与合同
        CommercialTermPlan plan = loader.plan(request.getPlanId());
        BusinessChangeHistory root = loader.planRoot(plan);
        long expected = loader.expectedVersion(root, request.getExpectedChangeVersion());
        Agreement agreement = versionAgreement(plan, request);

        // 复制来源与新版本
        CommercialTermVersion source = source(plan, request);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CommercialTermVersion version = newVersion(plan, agreement, source, request, now);
        CommercialTermVersionContext.CommercialTermVersionContextBuilder builder = CommercialTermVersionContext.builder()
            .now(now).triggerId(loader.nextId()).operatorAccountId(request.getOperatorAccountId())
            .expectedChangeVersion(expected).plan(plan).planHistory(root).agreement(agreement).version(version)
            .versionHistory(newChild(root, version, expected + 1, now));
        if (source != null) {
            copyPriceSet(builder, source, version, now);
        }

        // 保存
        CommercialTermVersionContext context = builder.build();
        commercialTermService.createVersion(context);
        return context.toVersionView();
    }

    /** Client 条款取方案合同（请求合同须为空或相等）；Partner 条款取请求合同，须为同 Local Partner 的 PARTNER 合同。 */
    Agreement versionAgreement(CommercialTermPlan plan, CreateCommercialTermVersionRequest request) {
        if (plan.getAgreementId() != null) {
            if (request.getAgreementId() != null && !request.getAgreementId().equals(plan.getAgreementId())) {
                throw new BizCheckFailException("Client 条款版本只能冻结方案合同: " + plan.getAgreementId());
            }
            return loader.openAgreement(plan.getAgreementId());
        }
        if (request.getAgreementId() == null) {
            throw new BizCheckFailException("Partner 条款版本必须指定 PARTNER 合同: 方案 " + plan.getTermPlanId());
        }
        Agreement agreement = loader.openAgreement(request.getAgreementId());
        if (agreement.getAgreementType() != AgreementAgreementType.PARTNER
            || !plan.getLocalPartnerId().equals(agreement.getLocalPartnerId())) {
            throw new BizCheckFailException("合同与方案 Local Partner 不符: 合同 " + agreement.getAgreementId());
        }
        return agreement;
    }

    /** 复制来源须属于同一方案；未复制时定价模型与含税口径必填。 */
    CommercialTermVersion source(CommercialTermPlan plan, CreateCommercialTermVersionRequest request) {
        if (request.getCopyFromVersionId() == null) {
            if (request.getPricingModel() == null || request.getFeeTaxTreatment() == null) {
                throw new BizCheckFailException("不复制时 pricingModel 与 feeTaxTreatment 必填");
            }
            return null;
        }
        CommercialTermVersion source = loader.version(request.getCopyFromVersionId());
        if (!source.getTermPlanId().equals(plan.getTermPlanId())) {
            throw new BizCheckFailException("复制来源版本不属于该方案: " + request.getCopyFromVersionId());
        }
        return source;
    }

    /** 新 DRAFT 版本。 */
    CommercialTermVersion newVersion(CommercialTermPlan plan, Agreement agreement, CommercialTermVersion source,
        CreateCommercialTermVersionRequest request, Instant now) {
        Long maxVersionNo = commercialTermVersionRepository.findMaxVersionNo(plan.getTermPlanId());
        PricingModel pricingModel = request.getPricingModel() != null ? request.getPricingModel() : source.getPricingModel();
        FeeTaxTreatment feeTaxTreatment =
            request.getFeeTaxTreatment() != null ? request.getFeeTaxTreatment() : source.getFeeTaxTreatment();
        CommercialTermVersion version = new CommercialTermVersion();
        version.setTermVersionId(loader.nextId());
        version.setTermPlanId(plan.getTermPlanId());
        version.setAgreementId(agreement.getAgreementId());
        version.setVersionNo(maxVersionNo == null ? 1L : maxVersionNo + 1);
        version.setStatus(CommercialTermVersionStatus.DRAFT);
        version.setActiveSlot(null);
        version.setEffectiveFrom(now);
        version.setEffectiveTo(null);
        version.setRoundingMode(CommercialTermRoundingMode.HALF_UP);
        version.setPricingModel(pricingModel);
        version.setFeeTaxTreatment(feeTaxTreatment);
        version.setCreateTime(now);
        version.setUpdateTime(now);
        return version;
    }

    /** 版本的 BCR Child，指向本次根版本。 */
    BusinessChangeHistory newChild(BusinessChangeHistory root, CommercialTermVersion version, long rootVersion, Instant now) {
        BusinessChangeHistory child = new BusinessChangeHistory();
        child.setHistoryId(loader.nextId());
        child.setRecordType(BusinessChangeRecordRecordType.COMMERCIAL_TERM_VERSION);
        child.setRecordId(Long.toString(version.getTermVersionId()));
        child.setAggregateRootType(BusinessChangeRecordAggregateRootType.COMMERCIAL_TERM_PLAN);
        child.setAggregateRootId(root.getAggregateRootId());
        child.setParentHistoryId(root.getHistoryId());
        child.setCurrentVersion(rootVersion);
        child.setCreateTime(now);
        child.setUpdateTime(now);
        return child;
    }

    /** 以新 ID 复制来源版本的价格、组件与档位。 */
    void copyPriceSet(CommercialTermVersionContext.CommercialTermVersionContextBuilder builder, CommercialTermVersion source,
        CommercialTermVersion version, Instant now) {
        List<CommercialTermPrice> prices = loader.prices(source.getTermVersionId());
        List<CommercialTermComponent> components = loader.components(prices);
        List<CommercialTermComponentBand> bands = loader.bands(components);
        Map<Long, Long> priceIds = new HashMap<>();
        Map<Long, Long> componentIds = new HashMap<>();
        for (CommercialTermPrice price : prices) {
            priceIds.put(price.getTermPriceId(), loader.nextId());
            builder.price(TermPriceRows.copy(price, priceIds.get(price.getTermPriceId()), version.getTermVersionId(), now));
        }
        for (CommercialTermComponent component : components) {
            componentIds.put(component.getTermComponentId(), loader.nextId());
            builder.component(TermPriceRows.copy(component, componentIds.get(component.getTermComponentId()),
                priceIds.get(component.getTermPriceId()), now));
        }
        for (CommercialTermComponentBand band : bands) {
            builder.band(TermPriceRows.copy(band, loader.nextId(), componentIds.get(band.getTermComponentId()), now));
        }
    }
}
