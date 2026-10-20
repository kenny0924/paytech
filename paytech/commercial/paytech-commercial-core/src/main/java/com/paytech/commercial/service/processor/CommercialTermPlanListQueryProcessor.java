package com.paytech.commercial.service.processor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermPlanListQuery;
import com.paytech.commercial.domain.CommercialTermPlanSummaryView;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 商业条款方案列表（运营控制台第二切片 technique.md §2）：只读，不加锁、不开显式事务
 * <li>每行附带方案 BCR 根当前版本与当前 ACTIVE / 已排期 SCHEDULED 版本；各查询为相邻的一致性读。</li>
 */
@Component
@AllArgsConstructor
public class CommercialTermPlanListQueryProcessor
    extends AbstractProcessor<CommercialTermPlanListQuery, PagingResponseBody<CommercialTermPlanSummaryView>> {
    /** 方案摘要需要定位的版本状态。 */
    private static final List<CommercialTermVersionStatus> POINTED_STATUSES = List.of(CommercialTermVersionStatus.ACTIVE,
        CommercialTermVersionStatus.SCHEDULED);

    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;

    @Override
    protected PagingResponseBody<CommercialTermPlanSummaryView> processRequest(CommercialTermPlanListQuery query) {
        List<CommercialTermPlan> plans = commercialTermPlanRepository.findPage(query);
        long total = commercialTermPlanRepository.count(query);
        List<Long> planIds = plans.stream().map(CommercialTermPlan::getTermPlanId).toList();
        Map<Long, Long> changeVersions = businessChangeRecordRepository.findRootVersions(
            BusinessChangeRecordAggregateRootType.COMMERCIAL_TERM_PLAN, planIds);
        Map<Long, List<CommercialTermVersion>> versions = commercialTermVersionRepository
            .findByPlanIdsAndStatuses(planIds, POINTED_STATUSES).stream()
            .collect(Collectors.groupingBy(CommercialTermVersion::getTermPlanId));
        List<CommercialTermPlanSummaryView> items = plans.stream().map(plan -> new CommercialTermPlanSummaryView(plan,
            changeVersions.get(plan.getTermPlanId()), versions.getOrDefault(plan.getTermPlanId(), List.of()))).toList();
        return new PagingResponseBody<>(items, query.getPaging().page(), query.getPaging().size(), total);
    }
}
