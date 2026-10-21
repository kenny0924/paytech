package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityDimensionRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.processor.request.UpdateAgreementCapabilityRequest;
import com.paytech.commercial.service.processor.response.AgreementCapabilityResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 整体改写开通项（PUT /agreements/v1/{agreementId}/capabilities/{agreementCapabilityId}），锁 {@code agreement:{id}}
 * <li>原请求：开通项必须属于该合同；状态、区间、保证金、接受条件与 3DS 要求均与当前一致时不写入，直接返回当前版本。</li>
 * <li>新请求：期望版本一致（合同 BCR Root CAS）、合同未结束；接受条件维度在可开通项可配维度内且必填维度给出（目录已停用不影响已开通项）；
 * 状态在 ENABLED / SUSPENDED / DISABLED 间自由切换。状态与条款条件更新、条件行整体替换、Root CAS 与 AGREEMENT_CAPABILITY UPDATE 快照
 * 同一事务写入。</li>
 */
public class UpdateAgreementCapabilityProcessor
    extends AbstractAgreementWriteProcessor<UpdateAgreementCapabilityRequest, AgreementCapabilityResponse> {
    private final IdGenerator idGenerator;
    private final AgreementService agreementService;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final AgreementCapabilityLimitRepository agreementCapabilityLimitRepository;
    private final ProductCapabilityDimensionRepository productCapabilityDimensionRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param agreementService 合同写入服务
     * @param agreementRepository 合同仓储
     * @param agreementCapabilityRepository 开通项仓储
     * @param businessChangeRecordRepository BCR 仓储
     * @param agreementCapabilityLimitRepository 条件仓储
     * @param productCapabilityDimensionRepository 可配维度仓储
     */
    public UpdateAgreementCapabilityProcessor(Clock clock, IdGenerator idGenerator, AgreementService agreementService,
        AgreementRepository agreementRepository, AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementCapabilityLimitRepository agreementCapabilityLimitRepository,
        ProductCapabilityDimensionRepository productCapabilityDimensionRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.idGenerator = idGenerator;
        this.agreementService = agreementService;
        this.agreementCapabilityRepository = agreementCapabilityRepository;
        this.agreementCapabilityLimitRepository = agreementCapabilityLimitRepository;
        this.productCapabilityDimensionRepository = productCapabilityDimensionRepository;
    }

    /** 请求所指合同。 */
    @Override
    protected long agreementId(UpdateAgreementCapabilityRequest request) {
        return request.getAgreementId();
    }

    /** 生效区间、保证金成组与接受条件规范化只依赖输入。 */
    @Override
    protected void validate(UpdateAgreementCapabilityRequest request) {
        AgreementCapabilityValidator.checkTerms(request);
    }

    @Override
    protected AgreementCapabilityResponse processWithinLock(UpdateAgreementCapabilityRequest request) {
        // 查询合同、开通项与内容是否已一致
        Agreement agreement = getAgreement(request.getAgreementId());
        BusinessChangeRecord root = getRoot(agreement);
        AgreementCapability capability = getCapability(agreement, request.getAgreementCapabilityId());
        List<AgreementCapabilityLimit> limits = agreementCapabilityLimitRepository
            .findByAgreementCapabilityId(capability.getAgreementCapabilityId());
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now(),
            request.getExpectedChangeVersion());
        context.setCapability(capability);
        if (unchanged(capability, limits, request)) {
            return AddAgreementCapabilityProcessor.capabilityResponse(context);
        }

        // 构建上下文
        prepareChange(context, request);

        // 保存上下文
        agreementService.updateCapability(context);

        // 响应处理
        return AddAgreementCapabilityProcessor.capabilityResponse(context);
    }

    /** 开通项必须属于请求合同。 */
    AgreementCapability getCapability(Agreement agreement, long agreementCapabilityId) {
        AgreementCapability capability = agreementCapabilityRepository.findById(agreementCapabilityId);
        if (capability == null || !capability.getAgreementId().equals(agreement.getAgreementId())) {
            throw new BizCheckFailException("开通项不存在或不属于该合同: " + agreementCapabilityId + ", 合同: "
                + agreement.getAgreementId());
        }
        return capability;
    }

    /** 状态、条款、接受条件与 3DS 要求是否均与当前一致。 */
    static boolean unchanged(AgreementCapability capability, List<AgreementCapabilityLimit> limits,
        UpdateAgreementCapabilityRequest request) {
        Set<String> current = limits.stream().map(limit -> limit.getLimitCode().getCode() + ":" + limit.getLimitValue())
            .collect(Collectors.toSet());
        Set<String> requested = request.getConditions().stream().flatMap(condition -> condition.getValues().stream()
            .map(value -> condition.getDimensionCode().getCode() + ":" + value)).collect(Collectors.toSet());
        boolean sameRate = capability.getRollingReserveRate() == null ? request.getRollingReserveRate() == null
            : request.getRollingReserveRate() != null
                && capability.getRollingReserveRate().compareTo(request.getRollingReserveRate()) == 0;
        return capability.getStatus() == request.getStatus()
            && Objects.equals(capability.getEffectiveFrom(), request.getEffectiveFrom())
            && Objects.equals(capability.getEffectiveTo(), request.getEffectiveTo()) && sameRate
            && Objects.equals(capability.getRollingReserveDays(), request.getRollingReserveDays())
            && Objects.equals(capability.getRequire3ds(), request.getRequire3ds()) && current.equals(requested);
    }

    /** 校验版本、合同状态与接受条件，准备目标值载体与新的条件行。 */
    void prepareChange(AgreementContext context, UpdateAgreementCapabilityRequest request) {
        checkVersion(context.getRoot(), request.getExpectedChangeVersion());
        checkOpen(context.getAgreement(), "修改开通项");
        String capabilityCode = context.getCapability().getCapabilityCode();
        AgreementCapabilityValidator.checkConditionsAllowed(request.getConditions(),
            productCapabilityDimensionRepository.findByCapabilityCodes(List.of(capabilityCode)), capabilityCode);
        AgreementCapability change = new AgreementCapability();
        change.setStatus(request.getStatus());
        change.setEffectiveFrom(request.getEffectiveFrom());
        change.setEffectiveTo(request.getEffectiveTo());
        change.setRollingReserveRate(request.getRollingReserveRate());
        change.setRollingReserveDays(request.getRollingReserveDays());
        change.setRequire3ds(request.getRequire3ds());
        context.setCapabilityChange(change);
        Instant now = context.getNow();
        context.setCapabilityLimits(AddAgreementCapabilityProcessor.buildLimits(idGenerator,
            context.getCapability().getAgreementCapabilityId(), request, now));
    }
}
