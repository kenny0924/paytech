package com.paytech.commercial.domainservice.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.api.event.AgreementStatusChangedPayload;
import com.paytech.commercial.api.event.CommercialTermVersionActivatedPayload;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialEventPayloads;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.service.CommercialEventPublicationService;

import lombok.AllArgsConstructor;

/**
 * CommercialEventPublicationService 实现：只访问 mor_commercial，读取由注入的只读 TransactionTemplate 包裹
 * <li>TODO by CLAUDE: 根修订已前进（发布前同一聚合又被修改）目前返回 null 由 Handler 终态保留；B / C 线接入 BCR 写入后，
 * 可改为按 t_business_change_version 中该版本的快照重建，避免人工处理。</li>
 */
@AllArgsConstructor
public class CommercialEventPublicationServiceImpl implements CommercialEventPublicationService {
    private final TransactionTemplate readTransaction;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final AgreementRepository agreementRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;

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
