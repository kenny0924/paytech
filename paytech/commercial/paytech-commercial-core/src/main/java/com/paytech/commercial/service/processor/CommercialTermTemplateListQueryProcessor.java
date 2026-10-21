package com.paytech.commercial.service.processor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermTemplateListQuery;
import com.paytech.commercial.domain.CommercialTermTemplateSummaryView;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 标准价目模板方案列表（GET /commercial-terms/v1/templates）：只读，不加锁、不开显式事务
 * <li>模板按 OE 维护、规模有限，不分页；每行附带方案 BCR 根版本、当前 ACTIVE 版本与版本号最大的版本。</li>
 */
@Component
@AllArgsConstructor
public class CommercialTermTemplateListQueryProcessor
    extends AbstractProcessor<CommercialTermTemplateListQuery, List<CommercialTermTemplateSummaryView>> {
    /** 全部版本状态。 */
    private static final List<CommercialTermVersionStatus> ALL_STATUSES = List.of(CommercialTermVersionStatus.values());

    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;

    @Override
    protected List<CommercialTermTemplateSummaryView> processRequest(CommercialTermTemplateListQuery query) {
        List<CommercialTermPlan> plans = commercialTermPlanRepository.findTemplates(query.getEntityNo(), query.getTermType(),
            query.getStatus());
        List<Long> planIds = plans.stream().map(CommercialTermPlan::getTermPlanId).toList();
        Map<Long, Long> changeVersions = businessChangeRecordRepository.findRootVersions(
            BusinessChangeRecordAggregateRootType.COMMERCIAL_TERM_PLAN, planIds);
        Map<Long, List<CommercialTermVersion>> versions = planIds.isEmpty() ? Map.of()
            : commercialTermVersionRepository.findByPlanIdsAndStatuses(planIds, ALL_STATUSES).stream()
                .collect(Collectors.groupingBy(CommercialTermVersion::getTermPlanId));
        return plans.stream().map(plan -> {
            List<CommercialTermVersion> planVersions = versions.getOrDefault(plan.getTermPlanId(), List.of());
            return new CommercialTermTemplateSummaryView(plan, changeVersions.get(plan.getTermPlanId()),
                planVersions.stream().filter(version -> version.getStatus() == CommercialTermVersionStatus.ACTIVE).findFirst()
                    .orElse(null),
                planVersions.stream().max(Comparator.comparing(CommercialTermVersion::getVersionNo)).orElse(null));
        }).toList();
    }
}
