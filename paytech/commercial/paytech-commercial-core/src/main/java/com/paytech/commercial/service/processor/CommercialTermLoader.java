package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeHistory;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/**
 * 条款写入 Processor 共用的锁内查询：方案、版本、BCR 根与 Child、合同覆盖 OE 及激活校验输入
 * <li>只读本模块 Repository，不写入、不开事务；查询结果由各 Processor 组装进同一个 CommercialTermVersionContext。</li>
 */
@Component
@AllArgsConstructor
public class CommercialTermLoader {
    /** 仍可挂接条款的合同状态。 */
    static final Set<AgreementStatus> OPEN_AGREEMENT_STATUSES = Set.of(AgreementStatus.DRAFT,
        AgreementStatus.PENDING_SIGNATURE, AgreementStatus.SIGNED, AgreementStatus.ACTIVE);

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AgreementRepository agreementRepository;
    private final PricingProfileRepository pricingProfileRepository;
    private final ProductFeeEventRepository productFeeEventRepository;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final PricingProfileCondRepository pricingProfileCondRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final AgreementCapabilityLimitRepository agreementCapabilityLimitRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;

    /**
     * 版本所属方案 ID，供锁 Key 使用（版本与方案的归属不可修改，可在锁外读取）。
     *
     * @param versionId 版本 ID
     * @return 方案 ID
     */
    public long planIdOf(long versionId) {
        return version(versionId).getTermPlanId();
    }

    /** 读取版本，不存在为业务检查失败。 */
    public CommercialTermVersion version(long versionId) {
        CommercialTermVersion version = commercialTermVersionRepository.findById(versionId);
        if (version == null) {
            throw new BizCheckFailException("商业条款版本不存在: " + versionId);
        }
        return version;
    }

    /** 读取方案，不存在为业务检查失败。 */
    public CommercialTermPlan plan(long planId) {
        CommercialTermPlan plan = commercialTermPlanRepository.findById(planId);
        if (plan == null) {
            throw new BizCheckFailException("商业条款方案不存在: " + planId);
        }
        return plan;
    }

    /** 读取仍可挂接条款的合同。 */
    public Agreement openAgreement(long agreementId) {
        Agreement agreement = agreementRepository.findById(agreementId);
        if (agreement == null) {
            throw new BizCheckFailException("合同不存在: " + agreementId);
        }
        if (!OPEN_AGREEMENT_STATUSES.contains(agreement.getStatus())) {
            throw new BizCheckFailException("合同已结束，不能挂接条款: " + agreementId + ", 状态 " + agreement.getStatus().getCode());
        }
        return agreement;
    }

    /** 方案的 BCR 根；缺失为系统数据不一致。 */
    public BusinessChangeHistory planRoot(CommercialTermPlan plan) {
        return history(BusinessChangeRecordRecordType.COMMERCIAL_TERM_PLAN, plan.getTermPlanId());
    }

    /** 版本的 BCR Child；缺失为系统数据不一致。 */
    public BusinessChangeHistory versionChild(CommercialTermVersion version) {
        return history(BusinessChangeRecordRecordType.COMMERCIAL_TERM_VERSION, version.getTermVersionId());
    }

    /**
     * 校验并返回期望根版本：运营命令须等于当前值（否则 409），系统命令取锁内当前值。
     *
     * @param root 方案 BCR 根
     * @param requested 请求携带的版本，系统命令为空
     * @return 期望版本
     */
    public long expectedVersion(BusinessChangeHistory root, Long requested) {
        if (requested == null) {
            return root.getCurrentVersion();
        }
        if (requested.longValue() != root.getCurrentVersion()) {
            throw new ConcurrentRequestException("方案已被修改，期望版本 " + requested + "，当前 " + root.getCurrentVersion()
                + ": " + root.getAggregateRootId());
        }
        return requested;
    }

    /** 合同覆盖的准确 OE：合同 entity_no 与覆盖表的并集。 */
    public List<String> coveredEntityNos(Agreement agreement) {
        List<String> covered = new ArrayList<>();
        if (agreement.getEntityNo() != null) {
            covered.add(agreement.getEntityNo());
        }
        agreementOperatingEntityRepository.findByAgreementId(agreement.getAgreementId()).stream()
            .map(AgreementOperatingEntity::getEntityNo).filter(entityNo -> !covered.contains(entityNo)).forEach(covered::add);
        return covered;
    }

    /**
     * 版本命令 Context 的公共部分：时间、关联 ID、操作人、方案、根、版本、Child、合同与覆盖 OE。
     *
     * @param request 版本命令
     * @param version 已读取的版本
     * @return 可继续追加命令专属字段的 Builder
     */
    public CommercialTermVersionContext.CommercialTermVersionContextBuilder commandContext(
        CommercialTermVersionCommandRequest request, CommercialTermVersion version) {
        CommercialTermPlan plan = plan(version.getTermPlanId());
        BusinessChangeHistory root = planRoot(plan);
        Agreement agreement = agreementRepository.findById(version.getAgreementId());
        if (agreement == null) {
            throw new ErrorException("版本冻结的合同不存在: " + version.getTermVersionId());
        }
        return CommercialTermVersionContext.builder().now(clock.instant().truncatedTo(ChronoUnit.MICROS))
            .triggerId(idGenerator.nextId()).systemCommand(request.systemCommand())
            .operatorAccountId(request.systemCommand() ? 0L : request.getOperatorAccountId())
            .expectedChangeVersion(expectedVersion(root, request.getExpectedChangeVersion())).plan(plan).planHistory(root)
            .agreement(agreement).coveredEntityNos(coveredEntityNos(agreement)).version(version)
            .versionHistory(versionChild(version));
    }

    /** 版本的全部价格。 */
    public List<CommercialTermPrice> prices(long versionId) {
        return commercialTermPriceRepository.findByVersionId(versionId);
    }

    /** 一组价格的全部组件。 */
    public List<CommercialTermComponent> components(List<CommercialTermPrice> prices) {
        return commercialTermComponentRepository.findByPriceIds(prices.stream().map(CommercialTermPrice::getTermPriceId).toList());
    }

    /** 一组组件的全部档位。 */
    public List<CommercialTermComponentBand> bands(List<CommercialTermComponent> components) {
        return commercialTermComponentBandRepository.findByComponentIds(
            components.stream().map(CommercialTermComponent::getTermComponentId).toList());
    }

    /**
     * 追加激活校验输入：价格集合、画像与条件、合同开通项与约束、可开通项目录、条款类型计费事件、同方案全部版本。
     *
     * @param builder Context Builder
     * @param plan 方案
     * @param version 版本
     * @return 同一 Builder
     */
    public CommercialTermVersionContext.CommercialTermVersionContextBuilder activationInputs(
        CommercialTermVersionContext.CommercialTermVersionContextBuilder builder, CommercialTermPlan plan,
        CommercialTermVersion version) {
        List<CommercialTermPrice> prices = prices(version.getTermVersionId());
        List<CommercialTermComponent> components = components(prices);
        List<PricingProfile> profiles = pricingProfileRepository.findByCodes(
            prices.stream().map(CommercialTermPrice::getPricingProfileCode).collect(Collectors.toSet()));
        List<AgreementCapability> capabilities = agreementCapabilityRepository.findByAgreementId(version.getAgreementId());
        return builder.prices(prices).components(components).bands(bands(components)).profiles(profiles)
            .profileConditions(pricingProfileCondRepository.findByProfileIds(
                profiles.stream().map(PricingProfile::getPricingProfileId).toList()))
            .capabilities(capabilities)
            .capabilityLimits(agreementCapabilityLimitRepository.findByAgreementCapabilityIds(
                capabilities.stream().map(AgreementCapability::getAgreementCapabilityId).toList()))
            .productCapabilities(productCapabilityRepository.findByCodes(
                capabilities.stream().map(AgreementCapability::getCapabilityCode).collect(Collectors.toSet())))
            .feeEvents(productFeeEventRepository.findByTermType(plan.getTermType()))
            .planVersions(commercialTermVersionRepository.findByPlanId(plan.getTermPlanId()));
    }

    /**
     * 同方案当前 ACTIVE 版本（不含本版本），至多一个；多于一个为系统数据不一致。
     *
     * @param planVersions 同方案全部版本
     * @param version 本版本
     * @return 当前 ACTIVE 版本，没有为 null
     */
    public CommercialTermVersion currentActive(List<CommercialTermVersion> planVersions, CommercialTermVersion version) {
        List<CommercialTermVersion> active = planVersions.stream()
            .filter(other -> other.getStatus() == CommercialTermVersionStatus.ACTIVE
                && !other.getTermVersionId().equals(version.getTermVersionId()))
            .toList();
        if (active.size() > 1) {
            throw new ErrorException("方案存在多个 ACTIVE 版本: " + version.getTermPlanId());
        }
        return active.isEmpty() ? null : active.getFirst();
    }

    /**
     * 追加前一 ACTIVE 版本及其 Child（排期关闭其窗口、激活将其替代）。
     *
     * @param builder 已含激活校验输入的 Builder
     * @param version 本版本
     * @return 同一 Builder
     */
    public CommercialTermVersionContext.CommercialTermVersionContextBuilder previousActive(
        CommercialTermVersionContext.CommercialTermVersionContextBuilder builder, CommercialTermVersion version) {
        CommercialTermVersion previous = currentActive(builder.build().getPlanVersions(), version);
        if (previous != null) {
            builder.supersededVersion(previous).supersededHistory(versionChild(previous));
        }
        return builder;
    }

    /** 新的本地 ID。 */
    public long nextId() {
        return idGenerator.nextId();
    }

    /** 读取一条审计身份；缺失为系统数据不一致。 */
    BusinessChangeHistory history(BusinessChangeRecordRecordType recordType, long recordId) {
        BusinessChangeHistory history = businessChangeRecordRepository.findByRecord(recordType, Long.toString(recordId));
        if (history == null) {
            throw new ErrorException("缺少 BCR 审计身份: " + recordType.getCode() + ":" + recordId);
        }
        return history;
    }
}
