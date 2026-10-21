package com.paytech.commercial.domainservice.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domain.AgreementDetailView;
import com.paytech.commercial.domain.BusinessChangeSnapshot;
import com.paytech.commercial.domain.enums.BusinessChangeOperation;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementDocumentRefRepository;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.commercial.domainservice.repository.AgreementProductRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/**
 * AgreementService 实现：写入使用注入的写事务模板，详情读取使用只读 REPEATABLE READ 模板
 * <li>BCR 写入委托 {@link BusinessChangeHistoryWriter}；事件只经 CompensationEventRegistrar 在同一事务登记，提交后由 Foundation 唤醒
 * AgreementStatusChangedHandler 发布。</li>
 */
@AllArgsConstructor
public class AgreementServiceImpl implements AgreementService {
    private final AgreementRepository agreementRepository;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;
    private final AgreementPartyRepository agreementPartyRepository;
    private final AgreementProductRepository agreementProductRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final BusinessChangeHistoryWriter historyWriter;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final AgreementDocumentRefRepository agreementDocumentRefRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final AgreementCapabilityLimitRepository agreementCapabilityLimitRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;

    /** 合同、参与方、文件、覆盖 OE 与 Root 同一事务写入。 */
    @Override
    public void createAgreement(AgreementContext context) {
        writeTransaction.executeWithoutResult(status -> {
            Agreement agreement = context.getAgreement();
            agreementRepository.insert(agreement);
            agreementPartyRepository.insertAll(context.getParties());
            agreementDocumentRefRepository.insertAll(context.getNewDocuments());
            agreementOperatingEntityRepository.insertAll(context.getOperatingEntities());
            context.setRoot(historyWriter.createRoot(BusinessChangeRecordAggregateRootType.AGREEMENT,
                agreement.getAgreementId(), BusinessChangeReason.AGREEMENT_CREATED,
                BusinessChangeSnapshot.agreementData(agreement), context.getActorId(), context.getNow()));
        });
    }

    /** 文件、生命周期、Root 与发布任务同一事务写入。 */
    @Override
    public void changeLifecycle(AgreementContext context, BusinessChangeReason reason) {
        writeTransaction.executeWithoutResult(status -> {
            agreementDocumentRefRepository.insertAll(context.getNewDocuments());
            updateLifecycle(context);
            advanceRoot(context, reason);
            if (context.getStatusChangedEvent() != null) {
                compensationEventRegistrar.register(context.getStatusChangedEvent());
            }
        });
    }

    /** 同 Party 全部角色一次登记；全部签完时同一事务推进到 SIGNED。 */
    @Override
    public void recordAcceptance(AgreementContext context) {
        writeTransaction.executeWithoutResult(status -> {
            agreementDocumentRefRepository.insertAll(context.getNewDocuments());
            AgreementParty acceptance = context.getAcceptance();
            List<AgreementParty> roles = context.getParties();
            for (int index = 0; index < roles.size(); index++) {
                // 组内最小主键行占 slot=1，其余为空，使 acceptance_reference 不能跨组复用。
                acceptance.setAcceptanceRefSlot(index == 0 ? 1 : null);
                agreementPartyRepository.recordAcceptance(roles.get(index), acceptance, context.getNow());
            }
            if (context.getNextStatus() != null) {
                updateLifecycle(context);
            }
            advanceRoot(context, BusinessChangeReason.AGREEMENT_PARTY_ACCEPTED);
        });
    }

    /** Root CAS 在前，Child 快照取本次版本；删除行先留 DELETE 快照再删除。 */
    @Override
    public void replaceProducts(AgreementContext context) {
        writeTransaction.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, BusinessChangeReason.AGREEMENT_PRODUCTS_REPLACED);
            BusinessChangeRecord root = context.getRoot();
            for (AgreementProduct removed : context.getRemovedProducts()) {
                historyWriter.changeChild(root, BusinessChangeRecordRecordType.AGREEMENT_PRODUCT,
                    removed.getAgreementProductId(), BusinessChangeOperation.DELETE, triggerId,
                    BusinessChangeReason.AGREEMENT_PRODUCTS_REPLACED, BusinessChangeSnapshot.agreementProductData(removed),
                    context.getActorId(), context.getNow());
                agreementProductRepository.delete(removed);
            }
            for (AgreementProduct added : context.getAddedProducts()) {
                agreementProductRepository.insert(added);
                historyWriter.createChild(root, BusinessChangeRecordRecordType.AGREEMENT_PRODUCT,
                    added.getAgreementProductId(), triggerId, BusinessChangeReason.AGREEMENT_PRODUCTS_REPLACED,
                    BusinessChangeSnapshot.agreementProductData(added), context.getActorId(), context.getNow());
            }
        });
    }

    /** 开通项、约束行、Root 与开通项 CREATE 快照同一事务写入。 */
    @Override
    public void addCapability(AgreementContext context) {
        writeTransaction.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, BusinessChangeReason.AGREEMENT_CAPABILITY_ADDED);
            AgreementCapability capability = context.getCapability();
            agreementCapabilityRepository.insert(capability);
            agreementCapabilityLimitRepository.insertAll(context.getCapabilityLimits());
            historyWriter.createChild(context.getRoot(), BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY,
                capability.getAgreementCapabilityId(), triggerId, BusinessChangeReason.AGREEMENT_CAPABILITY_ADDED,
                BusinessChangeSnapshot.agreementCapabilityData(capability, context.getCapabilityLimits()),
                context.getActorId(), context.getNow());
        });
    }

    /** 只改写实际变化的部分；约束行整体替换，快照记录改写后的完整开通项。 */
    @Override
    public void updateCapability(AgreementContext context) {
        writeTransaction.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, BusinessChangeReason.AGREEMENT_CAPABILITY_UPDATED);
            AgreementCapability capability = context.getCapability();
            AgreementCapability change = context.getCapabilityChange();
            if (capability.getStatus() != change.getStatus()) {
                agreementCapabilityRepository.updateStatus(capability, change.getStatus(), context.getNow());
            }
            if (termsChanged(capability, change)) {
                agreementCapabilityRepository.updateTerms(capability, change.getEffectiveFrom(), change.getEffectiveTo(),
                    change.getRollingReserveRate(), change.getRollingReserveDays(), change.getRequire3ds(), context.getNow());
            }
            agreementCapabilityLimitRepository.deleteByAgreementCapabilityId(capability.getAgreementCapabilityId());
            agreementCapabilityLimitRepository.insertAll(context.getCapabilityLimits());
            historyWriter.changeChild(context.getRoot(), BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY,
                capability.getAgreementCapabilityId(), BusinessChangeOperation.UPDATE, triggerId,
                BusinessChangeReason.AGREEMENT_CAPABILITY_UPDATED,
                BusinessChangeSnapshot.agreementCapabilityData(capability, context.getCapabilityLimits()),
                context.getActorId(), context.getNow());
        });
    }

    /** 同一只读快照读取合同、子表与 Root 版本。 */
    @Override
    public AgreementDetailView getAgreementDetail(long agreementId) {
        return readTransaction.execute(status -> {
            Agreement agreement = agreementRepository.findById(agreementId);
            if (agreement == null) {
                throw new BizCheckFailException("合同不存在: " + agreementId);
            }
            Long version = businessChangeRecordRepository.findRootVersion(BusinessChangeRecordAggregateRootType.AGREEMENT,
                Long.toString(agreementId));
            if (version == null) {
                throw new ErrorException("合同缺少 BCR Root: " + agreementId);
            }
            List<AgreementCapability> capabilities = agreementCapabilityRepository.findByAgreementId(agreementId);
            List<AgreementCapabilityLimit> limits = agreementCapabilityLimitRepository.findByAgreementCapabilityIds(
                capabilities.stream().map(AgreementCapability::getAgreementCapabilityId).toList());
            return new AgreementDetailView(agreement, version, agreementPartyRepository.findByAgreementId(agreementId),
                agreementDocumentRefRepository.findByAgreementId(agreementId),
                agreementOperatingEntityRepository.findByAgreementId(agreementId),
                agreementProductRepository.findByAgreementId(agreementId), capabilities, limits);
        });
    }

    /** 以当前状态为条件写入目标状态与三个时间。 */
    void updateLifecycle(AgreementContext context) {
        agreementRepository.updateLifecycle(context.getAgreement(), context.getNextStatus(), context.getNextEffectiveTo(),
            context.getNextSignedTime(), context.getNextActivatedTime(), context.getNow());
    }

    /** Root CAS 并写合同最终快照，返回本命令 triggerId。 */
    long advanceRoot(AgreementContext context, BusinessChangeReason reason) {
        return historyWriter.advanceRoot(context.getRoot(), context.getExpectedChangeVersion(), reason,
            BusinessChangeSnapshot.agreementData(context.getAgreement()), context.getActorId(), context.getNow());
    }

    /** 生效区间或保证金条款是否变化。 */
    static boolean termsChanged(AgreementCapability current, AgreementCapability change) {
        return !Objects.equals(current.getEffectiveFrom(), change.getEffectiveFrom())
            || !Objects.equals(current.getEffectiveTo(), change.getEffectiveTo())
            || !sameDecimal(current.getRollingReserveRate(), change.getRollingReserveRate())
            || !Objects.equals(current.getRollingReserveDays(), change.getRollingReserveDays())
            || !Objects.equals(current.getRequire3ds(), change.getRequire3ds());
    }

    /** 十进制按数值比较，忽略尾零。 */
    static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
