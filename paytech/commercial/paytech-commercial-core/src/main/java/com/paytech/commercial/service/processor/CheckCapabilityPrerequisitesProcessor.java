package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.domain.CapabilityAcceptance;
import com.paytech.commercial.domain.CapabilityPrerequisiteContext;
import com.paytech.commercial.domain.CommercialPriceCandidate;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domain.relationship.QualificationSubject;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.domainservice.validator.term.TermVersionActivationValidator;
import com.paytech.commercial.integration.CheckoutQualificationClient;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.service.processor.request.CheckCapabilityPrerequisitesRequest;
import com.paytech.commercial.service.processor.response.CapabilityPrerequisiteCheckResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;

/**
 * 开通项前置门禁的唯一入口（开通项前置门禁 technique.md §7）：锁 {@code agreement:{id}}（LOCK，与合同其他写入同一资源锁）
 * <li>1 锁内读取合同、BCR Root、开通项与条件、合同各商业条款方案的 ACTIVE 版本价格（本库，无事务）；PARTNER 合同不进门禁（400）。</li>
 * <li>2 事务外调用 compliance 批量判定 Client 与各成员开通项 OE 的资格；不可用时①为 UNKNOWN，不报错。</li>
 * <li>3 构造上下文逐开通项判定 ①②④ 与目标执行状态。</li>
 * <li>4 有迁移时由 AgreementService 在一个短事务内 Root CAS、条件迁移执行状态、写开通项快照（系统身份 0）并登记
 * AgreementCapabilityStatusChanged 发布任务；无迁移不写库。</li>
 * <li>5 返回逐项结论。运营检查、补偿任务、资格事件与重查 Job 都调用本入口，判定只写一次。</li>
 */
public class CheckCapabilityPrerequisitesProcessor
    extends AbstractAgreementWriteProcessor<CheckCapabilityPrerequisitesRequest, CapabilityPrerequisiteCheckResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(CheckCapabilityPrerequisitesProcessor.class);

    private final CommercialTermLoader termLoader;
    private final AgreementService agreementService;
    private final CheckoutQualificationClient qualificationClient;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final AgreementCapabilityLimitRepository agreementCapabilityLimitRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param termLoader 条款读取协作者（价格候选与计费事件）
     * @param agreementService 合同写入服务
     * @param agreementRepository 合同仓储
     * @param qualificationClient compliance 资格判定端口
     * @param productCapabilityRepository 可开通项目录仓储
     * @param commercialTermPlanRepository 条款方案仓储
     * @param agreementCapabilityRepository 开通项仓储
     * @param businessChangeRecordRepository BCR 仓储
     * @param commercialTermVersionRepository 条款版本仓储
     * @param agreementCapabilityLimitRepository 开通项约束仓储
     */
    public CheckCapabilityPrerequisitesProcessor(Clock clock, CommercialTermLoader termLoader,
        AgreementService agreementService, AgreementRepository agreementRepository,
        CheckoutQualificationClient qualificationClient, ProductCapabilityRepository productCapabilityRepository,
        CommercialTermPlanRepository commercialTermPlanRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        CommercialTermVersionRepository commercialTermVersionRepository,
        AgreementCapabilityLimitRepository agreementCapabilityLimitRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.termLoader = termLoader;
        this.agreementService = agreementService;
        this.qualificationClient = qualificationClient;
        this.productCapabilityRepository = productCapabilityRepository;
        this.commercialTermPlanRepository = commercialTermPlanRepository;
        this.agreementCapabilityRepository = agreementCapabilityRepository;
        this.commercialTermVersionRepository = commercialTermVersionRepository;
        this.agreementCapabilityLimitRepository = agreementCapabilityLimitRepository;
    }

    /** 请求所指合同。 */
    @Override
    protected long agreementId(CheckCapabilityPrerequisitesRequest request) {
        return request.getAgreementId();
    }

    @Override
    protected CapabilityPrerequisiteCheckResponse processWithinLock(CheckCapabilityPrerequisitesRequest request) {
        // 查询合同与本库事实
        Agreement agreement = getClientAgreement(request.getAgreementId());
        BusinessChangeRecord root = getRoot(agreement);
        List<AgreementCapability> capabilities = agreementCapabilityRepository.findByAgreementId(agreement.getAgreementId());
        List<AgreementCapabilityLimit> limits = agreementCapabilityLimitRepository.findByAgreementCapabilityIds(
            capabilities.stream().map(AgreementCapability::getAgreementCapabilityId).toList());
        List<CapabilityAcceptance> acceptances = CapabilityAcceptance.of(capabilities, limits,
            productCapabilityRepository.findByCodes(
                capabilities.stream().map(AgreementCapability::getCapabilityCode).collect(Collectors.toSet())));

        // 事务外资格判定，构建上下文并判定
        Map<QualificationSubject, Boolean> qualifications = qualify(agreement, capabilities);
        CapabilityPrerequisiteContext context = new CapabilityPrerequisiteContext(agreement, root, now(), acceptances, limits,
            termCoverages(agreement, acceptances), qualifications);

        // 有迁移时短事务保存并登记发布任务
        if (!context.changes().isEmpty()) {
            context.setCapabilityStatusChangedEvent(AgreementCapabilityStatusChangedHandler.buildEvent(
                agreement.getAgreementId(), root.getCurrentVersion() + 1));
            agreementService.applyCapabilityExecutionChanges(context);
        }

        // 响应处理
        return response(context);
    }

    /** 合同必须存在且为 CLIENT 合同；PARTNER 合同的开通项不进门禁。 */
    Agreement getClientAgreement(long agreementId) {
        Agreement agreement = getAgreement(agreementId);
        if (agreement.getAgreementType() != AgreementAgreementType.CLIENT) {
            throw new BizCheckFailException("PARTNER 合同的开通项不进入前置门禁: " + agreementId);
        }
        return agreement;
    }

    /**
     * 事务外批量判定资格；没有成员开通项时不调用
     *
     * @param agreement 合同
     * @param capabilities 合同全部开通项
     * @return 结论；compliance 不可用时为 null（①记为 UNKNOWN，状态不迁移）
     */
    Map<QualificationSubject, Boolean> qualify(Agreement agreement, List<AgreementCapability> capabilities) {
        List<QualificationSubject> subjects = CapabilityPrerequisiteContext.qualificationSubjects(agreement, capabilities);
        if (subjects.isEmpty()) {
            return Map.of();
        }
        try {
            return qualificationClient.evaluate(subjects);
        } catch (ErrorException unavailable) {
            // 查询失败只影响本次结论（UNKNOWN），由重查与补偿重试兜底，不中断核对。
            LOG.warn("开通项前置核对资格查询不可用 agreementId={} code={}", agreement.getAgreementId(),
                unavailable.getCode());
            return null;
        }
    }

    /** 合同各商业条款方案：条款类型计费事件挂接的种类、ACTIVE 版本及其价格完整覆盖的成员开通项。 */
    List<CapabilityPrerequisiteContext.TermCoverage> termCoverages(Agreement agreement,
        List<CapabilityAcceptance> acceptances) {
        return commercialTermPlanRepository.findByAgreementId(agreement.getAgreementId()).stream()
            .map(plan -> termCoverage(plan, acceptances)).toList();
    }

    /** 一个方案的覆盖事实；没有 ACTIVE 版本时覆盖集合为空；未完整覆盖的开通项同时算出未覆盖的币种（原因参数）。 */
    CapabilityPrerequisiteContext.TermCoverage termCoverage(CommercialTermPlan plan, List<CapabilityAcceptance> acceptances) {
        Set<CapabilityKind> kinds = termLoader.feeEvents(plan.getTermType()).stream().map(ProductFeeEvent::getCapabilityKind)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        CommercialTermVersion active = commercialTermVersionRepository.findByPlanId(plan.getTermPlanId()).stream()
            .filter(version -> version.getStatus() == CommercialTermVersionStatus.ACTIVE).findFirst().orElse(null);
        Set<Long> covered = new HashSet<>();
        Map<Long, List<String>> uncovered = new HashMap<>();
        if (active != null) {
            List<CommercialPriceCandidate> candidates = termLoader.candidates(termLoader.prices(active.getTermVersionId()));
            for (CapabilityAcceptance acceptance : acceptances) {
                if (!acceptance.member() || !kinds.contains(acceptance.kind())) {
                    continue;
                }
                long capabilityId = acceptance.capability().getAgreementCapabilityId();
                if (TermVersionActivationValidator.coversCapability(candidates, acceptance)) {
                    covered.add(capabilityId);
                } else {
                    uncovered.put(capabilityId, TermVersionActivationValidator.uncoveredCurrencies(candidates, acceptance));
                }
            }
        }
        return new CapabilityPrerequisiteContext.TermCoverage(plan.getTermType(), kinds, active, covered, uncovered);
    }

    /** 从上下文构造结果；保存成功时 Root 与开通项已同步为新值。 */
    static CapabilityPrerequisiteCheckResponse response(CapabilityPrerequisiteContext context) {
        CapabilityPrerequisiteCheckResponse response = new CapabilityPrerequisiteCheckResponse();
        response.setAgreementId(context.getAgreement().getAgreementId());
        response.setChangeVersion(context.getRoot().getCurrentVersion());
        response.setChanged(!context.changes().isEmpty());
        response.setUnknown(context.anyUnknown());
        response.setDecisions(context.getDecisions());
        return response;
    }
}
