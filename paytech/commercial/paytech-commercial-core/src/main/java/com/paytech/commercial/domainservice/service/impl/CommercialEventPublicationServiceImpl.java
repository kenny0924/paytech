package com.paytech.commercial.domainservice.service.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.event.AgreementCapabilityStatusChangedPayload;
import com.paytech.commercial.api.event.AgreementStatusChangedPayload;
import com.paytech.commercial.api.event.CommercialTermVersionActivatedPayload;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.domain.BusinessChangeSnapshot;
import com.paytech.commercial.domain.CommercialEventPayloads;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.service.CommercialEventPublicationService;

import lombok.AllArgsConstructor;

/**
 * CommercialEventPublicationService 实现：只访问 mor_commercial，读取由注入的只读 TransactionTemplate 包裹
 * <li>TODO by CLAUDE: 根修订已前进（发布前同一聚合又被修改）目前返回 null 由 Handler 终态保留；B / C 线接入 BCR 写入后，
 * 可改为按 t_business_change_version 中该版本的快照重建，避免人工处理。</li>
 */
@AllArgsConstructor
public class CommercialEventPublicationServiceImpl implements CommercialEventPublicationService {
    /** 事件触发来源：前置门禁。 */
    static final String TRIGGER_PREREQUISITE = "PREREQUISITE";

    /** 事件触发来源：运营暂停 / 恢复。 */
    static final String TRIGGER_MANUAL = "MANUAL";

    /** 事件触发来源：新增、关闭或重新纳入。 */
    static final String TRIGGER_MEMBERSHIP = "MEMBERSHIP";

    private final TransactionTemplate readTransaction;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final AgreementRepository agreementRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final BusinessChangeVersionRepository businessChangeVersionRepository;

    /** 合同存在且 AGREEMENT 根修订等于事件版本时，按当前合同行组装载荷。 */
    @Override
    public AgreementStatusChangedPayload loadAgreementStatusChanged(long agreementId, long changeVersion) {
        return readTransaction.execute(status -> {
            if (!rootAt(BusinessChangeRecordAggregateRootType.AGREEMENT, agreementId, changeVersion)) {
                return null;
            }
            Agreement agreement = agreementRepository.findById(agreementId);
            return agreement == null ? null : CommercialEventPayloads.agreementStatusChanged(agreement, changeVersion);
        });
    }

    /** 方案存在、根修订等于事件版本且恰有一个 ACTIVE 版本时组装载荷；被替代版本取生效终点等于新版本起点的最近 SUPERSEDED 版本。 */
    @Override
    public CommercialTermVersionActivatedPayload loadTermVersionActivated(long planId, long changeVersion) {
        return readTransaction.execute(status -> {
            if (!rootAt(BusinessChangeRecordAggregateRootType.COMMERCIAL_TERM_PLAN, planId, changeVersion)) {
                return null;
            }
            CommercialTermPlan plan = commercialTermPlanRepository.findById(planId);
            if (plan == null) {
                return null;
            }
            List<CommercialTermVersion> versions = commercialTermVersionRepository.findByPlanId(planId);
            List<CommercialTermVersion> active = versions.stream()
                .filter(version -> version.getStatus() == CommercialTermVersionStatus.ACTIVE).toList();
            if (active.size() != 1) {
                return null;
            }
            return CommercialEventPayloads.termVersionActivated(plan, active.getFirst(),
                supersededBy(versions, active.getFirst()), changeVersion);
        });
    }

    /** 合同存在时，按各开通项 Child 在事件版本与之前最近一版的快照比较执行状态，组装 changes[]。 */
    @Override
    public AgreementCapabilityStatusChangedPayload loadAgreementCapabilityStatusChanged(long agreementId,
        long changeVersion) {
        return readTransaction.execute(status -> {
            Agreement agreement = agreementRepository.findById(agreementId);
            if (agreement == null) {
                return null;
            }
            List<BusinessChangeRecord> children = businessChangeRecordRepository
                .findByAggregateRoot(BusinessChangeRecordAggregateRootType.AGREEMENT, Long.toString(agreementId)).stream()
                .filter(record -> record.getRecordType() == BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY).toList();
            Map<Long, List<BusinessChangeVersion>> versions = businessChangeVersionRepository
                .findByHistoryIds(children.stream().map(BusinessChangeRecord::getHistoryId).toList()).stream()
                .collect(Collectors.groupingBy(BusinessChangeVersion::getHistoryId));
            List<CapabilityExecutionChange> changes = children.stream()
                .map(child -> executionChange(versions.getOrDefault(child.getHistoryId(), List.of()), changeVersion))
                .filter(Objects::nonNull).sorted(Comparator.comparing(CapabilityExecutionChange::agreementCapabilityId))
                .toList();
            if (changes.isEmpty()) {
                return null;
            }
            Map<String, String> kinds = kinds(changes);
            return CommercialEventPayloads.agreementCapabilityStatusChanged(agreement, changeVersion,
                changes.getFirst().occurredAt(), changes.stream().map(change -> change.toPayload(kinds)).toList());
        });
    }

    /** 本次变化涉及的可开通项代码 → 种类代码。 */
    Map<String, String> kinds(List<CapabilityExecutionChange> changes) {
        return productCapabilityRepository.findByCodes(changes.stream().map(CapabilityExecutionChange::capabilityCode)
            .collect(Collectors.toSet())).stream().collect(Collectors.toMap(ProductCapability::getCapabilityCode,
                catalog -> catalog.getCapabilityKind().getCode()));
    }

    /** 一个开通项在事件版本的执行状态变化；该版本没有快照或执行状态未变时为 null。 */
    static CapabilityExecutionChange executionChange(List<BusinessChangeVersion> versions, long changeVersion) {
        BusinessChangeVersion current = versions.stream().filter(version -> version.getVersionNo() == changeVersion)
            .findFirst().orElse(null);
        if (current == null) {
            return null;
        }
        BusinessChangeVersion previous = versions.stream().filter(version -> version.getVersionNo() < changeVersion)
            .max(Comparator.comparing(BusinessChangeVersion::getVersionNo)).orElse(null);
        String to = executionStatus(current.getSnapshotJson());
        String from = previous == null ? null : executionStatus(previous.getSnapshotJson());
        if (Objects.equals(from, to)) {
            return null;
        }
        String snapshot = current.getSnapshotJson();
        return new CapabilityExecutionChange(Long.parseLong(BusinessChangeSnapshot.dataText(snapshot, "agreementCapabilityId")),
            BusinessChangeSnapshot.dataText(snapshot, "entityNo"), BusinessChangeSnapshot.dataText(snapshot, "capabilityCode"),
            from, to, trigger(BusinessChangeSnapshot.reasonCode(snapshot)), current.getCreateTime());
    }

    /**
     * 快照中的执行状态；2026-10-28 之前的快照没有 executionStatus，按存量迁移映射从旧 status 推出
     * <li>ENABLED → ENABLED，SUSPENDED → SUSPENDED_MANUALLY，DISABLED / ACTIVE 之外的其他值 → 空。</li>
     */
    static String executionStatus(String snapshotJson) {
        if (BusinessChangeSnapshot.hasData(snapshotJson, "executionStatus")) {
            return BusinessChangeSnapshot.dataText(snapshotJson, "executionStatus");
        }
        String legacy = BusinessChangeSnapshot.dataText(snapshotJson, "status");
        if ("ENABLED".equals(legacy)) {
            return AgreementCapabilityExecutionStatus.ENABLED.getCode();
        }
        return "SUSPENDED".equals(legacy) ? AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY.getCode() : null;
    }

    /** 快照原因到事件触发来源：门禁 PREREQUISITE，运营暂停 / 恢复 MANUAL，其余（新增、改写成员资格）MEMBERSHIP。 */
    static String trigger(String reasonCode) {
        if (BusinessChangeReason.AGREEMENT_CAPABILITY_EXECUTION_CHANGED.getCode().equals(reasonCode)) {
            return TRIGGER_PREREQUISITE;
        }
        boolean manual = BusinessChangeReason.AGREEMENT_CAPABILITY_SUSPENDED.getCode().equals(reasonCode)
            || BusinessChangeReason.AGREEMENT_CAPABILITY_RESUMED.getCode().equals(reasonCode);
        return manual ? TRIGGER_MANUAL : TRIGGER_MEMBERSHIP;
    }

    /** 一个开通项在事件版本的执行状态变化（快照读出的值）。 */
    record CapabilityExecutionChange(long agreementCapabilityId, String entityNo, String capabilityCode,
        String fromExecutionStatus, String toExecutionStatus, String trigger, Instant occurredAt) {
        /** 转为载荷条目；种类取目录当前值。 */
        AgreementCapabilityStatusChangedPayload.Change toPayload(Map<String, String> kinds) {
            AgreementCapabilityStatusChangedPayload.Change change = new AgreementCapabilityStatusChangedPayload.Change();
            change.setAgreementCapabilityId(agreementCapabilityId);
            change.setEntityNo(entityNo);
            change.setCapabilityCode(capabilityCode);
            change.setCapabilityKind(kinds.get(capabilityCode));
            change.setFromExecutionStatus(fromExecutionStatus);
            change.setToExecutionStatus(toExecutionStatus);
            change.setTrigger(trigger);
            return change;
        }
    }

    /** 聚合根存在且当前修订等于事件版本。 */
    boolean rootAt(BusinessChangeRecordAggregateRootType rootType, long rootId, long changeVersion) {
        Long current = businessChangeRecordRepository.findRootVersion(rootType, Long.toString(rootId));
        return current != null && current == changeVersion;
    }

    /** 被新版本替代的前一版本：版本号更小、状态 SUPERSEDED 且生效终点等于新版本起点中版本号最大者；方案首次生效为 null。 */
    static CommercialTermVersion supersededBy(List<CommercialTermVersion> versions, CommercialTermVersion activated) {
        return versions.stream()
            .filter(version -> version.getStatus() == CommercialTermVersionStatus.SUPERSEDED)
            .filter(version -> version.getVersionNo() < activated.getVersionNo())
            .filter(version -> Objects.equals(version.getEffectiveTo(), activated.getEffectiveFrom()))
            .max(Comparator.comparing(CommercialTermVersion::getVersionNo)).orElse(null);
    }
}
