package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementProductRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityDimensionRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.service.handler.CapabilityPrerequisiteCheckHandler;
import com.paytech.commercial.service.processor.request.AddAgreementCapabilityRequest;
import com.paytech.commercial.service.processor.request.AgreementCapabilityTermsRequest;
import com.paytech.commercial.service.processor.response.AgreementCapabilityResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 新增开通项（POST /agreements/v1/{agreementId}/capabilities），锁 {@code agreement:{id}}
 * <li>业务唯一身份 uk(agreement, entityNo, capabilityCode)：已存在返回 409（technique.md §2.2 重复语义）。</li>
 * <li>新请求：期望版本一致、合同未结束；entityNo 被合同覆盖（CLIENT 等于合同 OE，PARTNER 在覆盖 OE 内）；可开通项存在且 ACTIVE，所属
 * 产品已订阅或为基座产品；接受条件维度在可开通项可配维度内且必填维度给出。开通项（成员资格 ACTIVE，含 3DS 要求）、条件行、Root CAS
 * 与 AGREEMENT_CAPABILITY CREATE 快照同一事务写入。</li>
 * <li>执行状态（开通项前置门禁 technique.md §9.1）：CLIENT 合同为 PENDING_PREREQUISITES，并在同一事务登记前置检查任务，提交后由门禁核对；
 * PARTNER 合同不进门禁，直接 ENABLED。两者都登记 AgreementCapabilityStatusChanged 发布任务（触发来源 MEMBERSHIP）。</li>
 */
public class AddAgreementCapabilityProcessor
    extends AbstractAgreementWriteProcessor<AddAgreementCapabilityRequest, AgreementCapabilityResponse> {
    private final IdGenerator idGenerator;
    private final AgreementService agreementService;
    private final ProductRepository productRepository;
    private final AgreementProductRepository agreementProductRepository;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;
    private final ProductCapabilityDimensionRepository productCapabilityDimensionRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param agreementService 合同写入服务
     * @param productRepository 产品目录仓储
     * @param agreementRepository 合同仓储
     * @param agreementProductRepository 订阅仓储
     * @param productCapabilityRepository 可开通项目录仓储
     * @param agreementCapabilityRepository 开通项仓储
     * @param businessChangeRecordRepository BCR 仓储
     * @param agreementOperatingEntityRepository 覆盖 OE 仓储
     * @param productCapabilityDimensionRepository 可配维度仓储
     */
    public AddAgreementCapabilityProcessor(Clock clock, IdGenerator idGenerator, AgreementService agreementService,
        ProductRepository productRepository, AgreementRepository agreementRepository,
        AgreementProductRepository agreementProductRepository, ProductCapabilityRepository productCapabilityRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository,
        ProductCapabilityDimensionRepository productCapabilityDimensionRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.idGenerator = idGenerator;
        this.agreementService = agreementService;
        this.productRepository = productRepository;
        this.agreementProductRepository = agreementProductRepository;
        this.productCapabilityRepository = productCapabilityRepository;
        this.agreementCapabilityRepository = agreementCapabilityRepository;
        this.agreementOperatingEntityRepository = agreementOperatingEntityRepository;
        this.productCapabilityDimensionRepository = productCapabilityDimensionRepository;
    }

    /** 请求所指合同。 */
    @Override
    protected long agreementId(AddAgreementCapabilityRequest request) {
        return request.getAgreementId();
    }

    /** 生效区间、保证金成组与接受条件规范化只依赖输入。 */
    @Override
    protected void validate(AddAgreementCapabilityRequest request) {
        AgreementCapabilityValidator.checkTerms(request);
    }

    @Override
    protected AgreementCapabilityResponse processWithinLock(AddAgreementCapabilityRequest request) {
        // 查询合同与已有开通项
        Agreement agreement = getAgreement(request.getAgreementId());
        BusinessChangeRecord root = getRoot(agreement);
        if (agreementCapabilityRepository.findByUniqueKey(agreement.getAgreementId(), request.getEntityNo(),
            request.getCapabilityCode()) != null) {
            throw new ConcurrentRequestException("开通项已存在: " + request.getEntityNo() + " " + request.getCapabilityCode()
                + ", 合同: " + agreement.getAgreementId());
        }

        // 构建上下文
        AgreementContext context = buildContext(agreement, root, request);

        // 保存上下文
        agreementService.addCapability(context);

        // 响应处理
        return capabilityResponse(context);
    }

    /** 校验版本、状态、OE 覆盖、目录资格与接受条件后构造开通项与条件行。 */
    AgreementContext buildContext(Agreement agreement, BusinessChangeRecord root, AddAgreementCapabilityRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        checkOpen(agreement, "新增开通项");
        checkEntityCovered(agreement, request.getEntityNo());
        checkCatalog(agreement, request.getCapabilityCode());
        AgreementCapabilityValidator.checkConditionsAllowed(request.getConditions(),
            productCapabilityDimensionRepository.findByCapabilityCodes(List.of(request.getCapabilityCode())),
            request.getCapabilityCode());
        Instant now = now();
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now,
            request.getExpectedChangeVersion());
        AgreementCapability capability = buildCapability(agreement, request, now);
        context.setCapability(capability);
        context.setCapabilityLimits(buildLimits(idGenerator, capability.getAgreementCapabilityId(), request, now));
        registerExecutionEvents(context, AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES
            == capability.getExecutionStatus());
        return context;
    }

    /** CLIENT 合同只覆盖自身 OE，PARTNER 合同覆盖其关联 OE。 */
    void checkEntityCovered(Agreement agreement, String entityNo) {
        boolean covered = agreement.getEntityNo() != null ? agreement.getEntityNo().equals(entityNo)
            : agreementOperatingEntityRepository.findByUniqueKey(agreement.getAgreementId(), entityNo) != null;
        if (!covered) {
            throw new BizCheckFailException("合同不覆盖该 OE: " + entityNo + ", 合同: " + agreement.getAgreementId());
        }
    }

    /** 可开通项 ACTIVE，所属产品已订阅或为基座产品。 */
    void checkCatalog(Agreement agreement, String capabilityCode) {
        ProductCapability catalog = productCapabilityRepository.findByCode(capabilityCode);
        if (catalog == null || catalog.getStatus() != ProductStatus.ACTIVE) {
            throw new BizCheckFailException("可开通项不存在或未启用: " + capabilityCode);
        }
        Product product = productRepository.findByCode(catalog.getProductCode());
        boolean base = product != null && Boolean.TRUE.equals(product.getBaseProduct());
        if (!base && agreementProductRepository.findByAgreementAndProduct(agreement.getAgreementId(),
            catalog.getProductCode()) == null) {
            throw new BizCheckFailException("可开通项所属产品未订阅: " + catalog.getProductCode().getCode() + ", 可开通项: "
                + capabilityCode);
        }
    }

    /**
     * 执行状态变化附带的任务：发布 AgreementCapabilityStatusChanged，等待前置时再登记前置检查；新增、改写、恢复共用
     *
     * @param context 带期望版本的上下文
     * @param pendingPrerequisites 本次是否进入 PENDING_PREREQUISITES
     */
    static void registerExecutionEvents(AgreementContext context, boolean pendingPrerequisites) {
        long agreementId = context.getAgreement().getAgreementId();
        long nextVersion = context.getExpectedChangeVersion() + 1;
        context.setCapabilityStatusChangedEvent(AgreementCapabilityStatusChangedHandler.buildEvent(agreementId, nextVersion));
        if (pendingPrerequisites) {
            context.setPrerequisiteCheckEvent(CapabilityPrerequisiteCheckHandler.buildEvent(agreementId, nextVersion));
        }
    }

    /**
     * 新增或重新纳入时的执行状态：CLIENT 合同等待前置，PARTNER 合同不进门禁直接开通
     *
     * @param agreement 合同
     * @return 执行状态
     */
    static AgreementCapabilityExecutionStatus initialExecutionStatus(Agreement agreement) {
        return agreement.getAgreementType() == AgreementAgreementType.CLIENT
            ? AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES : AgreementCapabilityExecutionStatus.ENABLED;
    }

    /** 成员资格 ACTIVE 的开通项新行，执行状态按合同类型决定。 */
    AgreementCapability buildCapability(Agreement agreement, AddAgreementCapabilityRequest request, Instant now) {
        AgreementCapability capability = new AgreementCapability();
        capability.setAgreementCapabilityId(idGenerator.nextId());
        capability.setAgreementId(agreement.getAgreementId());
        capability.setEntityNo(request.getEntityNo());
        capability.setCapabilityCode(request.getCapabilityCode());
        capability.setStatus(AgreementCapabilityStatus.ACTIVE);
        capability.setExecutionStatus(initialExecutionStatus(agreement));
        capability.setEffectiveFrom(request.getEffectiveFrom());
        capability.setEffectiveTo(request.getEffectiveTo());
        capability.setRollingReserveRate(request.getRollingReserveRate());
        capability.setRollingReserveDays(request.getRollingReserveDays());
        capability.setRequire3ds(request.getRequire3ds());
        capability.setCreateTime(now);
        capability.setUpdateTime(now);
        return capability;
    }

    /**
     * 条件行新行（每个取值一行）；新增与修改共用
     *
     * @param idGenerator 主键生成器
     * @param agreementCapabilityId 所属开通项
     * @param terms 条款请求
     * @param now 业务时刻
     * @return 条件行
     */
    static List<AgreementCapabilityLimit> buildLimits(IdGenerator idGenerator, long agreementCapabilityId,
        AgreementCapabilityTermsRequest terms, Instant now) {
        return terms.getConditions().stream().flatMap(condition -> condition.getValues().stream().map(value -> {
            AgreementCapabilityLimit row = new AgreementCapabilityLimit();
            row.setAgreementCapabilityLimitId(idGenerator.nextId());
            row.setAgreementCapabilityId(agreementCapabilityId);
            row.setLimitCode(condition.getDimensionCode());
            row.setLimitValue(value);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            return row;
        })).toList();
    }

    /**
     * 从上下文构造开通项写入结果；新增与修改共用
     *
     * @param context 已保存的上下文
     * @return 结果
     */
    static AgreementCapabilityResponse capabilityResponse(AgreementContext context) {
        AgreementCapabilityResponse response = new AgreementCapabilityResponse();
        response.setAgreementId(context.getAgreement().getAgreementId());
        response.setAgreementCapabilityId(context.getCapability().getAgreementCapabilityId());
        response.setStatus(context.getCapability().getStatus());
        response.setExecutionStatus(context.getCapability().getExecutionStatus());
        response.setChangeVersion(context.getRoot().getCurrentVersion());
        return response;
    }
}
