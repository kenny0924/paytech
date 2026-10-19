package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementProductRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
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
 * 产品已订阅或为基座产品。开通项（ENABLED）、约束行、Root CAS 与 AGREEMENT_CAPABILITY CREATE 快照同一事务写入。</li>
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
     */
    public AddAgreementCapabilityProcessor(Clock clock, IdGenerator idGenerator, AgreementService agreementService,
        ProductRepository productRepository, AgreementRepository agreementRepository,
        AgreementProductRepository agreementProductRepository, ProductCapabilityRepository productCapabilityRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.idGenerator = idGenerator;
        this.agreementService = agreementService;
        this.productRepository = productRepository;
        this.agreementProductRepository = agreementProductRepository;
        this.productCapabilityRepository = productCapabilityRepository;
        this.agreementCapabilityRepository = agreementCapabilityRepository;
        this.agreementOperatingEntityRepository = agreementOperatingEntityRepository;
    }

    /** 请求所指合同。 */
    @Override
    protected long agreementId(AddAgreementCapabilityRequest request) {
        return request.getAgreementId();
    }

    /** 生效区间、保证金成组与约束行规范化只依赖输入。 */
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

    /** 校验版本、状态、OE 覆盖与目录资格后构造开通项与约束行。 */
    AgreementContext buildContext(Agreement agreement, BusinessChangeRecord root, AddAgreementCapabilityRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        checkOpen(agreement, "新增开通项");
        checkEntityCovered(agreement, request.getEntityNo());
        checkCatalog(agreement, request.getCapabilityCode());
        Instant now = now();
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now,
            request.getExpectedChangeVersion());
        AgreementCapability capability = buildCapability(agreement, request, now);
        context.setCapability(capability);
        context.setCapabilityLimits(buildLimits(idGenerator, capability.getAgreementCapabilityId(), request, now));
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

    /** ENABLED 开通项新行。 */
    AgreementCapability buildCapability(Agreement agreement, AddAgreementCapabilityRequest request, Instant now) {
        AgreementCapability capability = new AgreementCapability();
        capability.setAgreementCapabilityId(idGenerator.nextId());
        capability.setAgreementId(agreement.getAgreementId());
        capability.setEntityNo(request.getEntityNo());
        capability.setCapabilityCode(request.getCapabilityCode());
        capability.setStatus(AgreementCapabilityStatus.ENABLED);
        capability.setEffectiveFrom(request.getEffectiveFrom());
        capability.setEffectiveTo(request.getEffectiveTo());
        capability.setRollingReserveRate(request.getRollingReserveRate());
        capability.setRollingReserveDays(request.getRollingReserveDays());
        capability.setCreateTime(now);
        capability.setUpdateTime(now);
        return capability;
    }

    /**
     * 约束行新行；新增与修改共用
     *
     * @param idGenerator 主键生成器
     * @param agreementCapabilityId 所属开通项
     * @param terms 条款请求
     * @param now 业务时刻
     * @return 约束行
     */
    static List<AgreementCapabilityLimit> buildLimits(IdGenerator idGenerator, long agreementCapabilityId,
        AgreementCapabilityTermsRequest terms, Instant now) {
        return terms.getLimits().stream().map(limit -> {
            AgreementCapabilityLimit row = new AgreementCapabilityLimit();
            row.setAgreementCapabilityLimitId(idGenerator.nextId());
            row.setAgreementCapabilityId(agreementCapabilityId);
            row.setLimitCode(limit.getLimitCode());
            row.setLimitValue(limit.getLimitValue());
            row.setCreateTime(now);
            row.setUpdateTime(now);
            return row;
        }).toList();
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
        response.setChangeVersion(context.getRoot().getCurrentVersion());
        return response;
    }
}
