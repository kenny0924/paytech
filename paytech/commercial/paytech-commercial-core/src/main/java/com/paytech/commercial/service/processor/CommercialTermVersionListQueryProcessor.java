package com.paytech.commercial.service.processor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.commercial.domainservice.service.CommercialTermInsightService;
import com.paytech.commercial.domain.CommercialTermVersionInsight;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermReviewFacts;
import com.paytech.commercial.domain.CommercialTermVersionListQuery;
import com.paytech.commercial.domain.CommercialTermVersionSummaryView;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 商业条款版本列表与审批工作台（运营控制台第二切片 technique.md §2）：只读，不加锁、不开显式事务
 * <li>每行附带所属方案摘要、方案 BCR 根当前版本（批准 / 驳回 / 排期的 expectedChangeVersion）、价格条数，以及由版本审计快照推导的
 * 本轮 Maker / Checker / 提交时刻、破标数与客户签署情况。各查询为相邻的一致性读。</li>
 */
@Component
@AllArgsConstructor
public class CommercialTermVersionListQueryProcessor
    extends AbstractProcessor<CommercialTermVersionListQuery, PagingResponseBody<CommercialTermVersionSummaryView>> {
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final BusinessChangeVersionRepository businessChangeVersionRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final CommercialTermInsightService commercialTermInsightService;

    /** 时间窗口两端都给出时终点必须晚于起点。 */
    @Override
    protected void validate(CommercialTermVersionListQuery query) {
        if (query.getWindowFrom() != null && query.getWindowTo() != null
            && !query.getWindowTo().isAfter(query.getWindowFrom())) {
            throw new InvalidParameterException("effectiveTo 必须晚于 effectiveFrom");
        }
    }

    @Override
    protected PagingResponseBody<CommercialTermVersionSummaryView> processRequest(CommercialTermVersionListQuery query) {
        List<CommercialTermVersion> versions = commercialTermVersionRepository.findPage(query);
        long total = commercialTermVersionRepository.count(query);
        List<Long> planIds = versions.stream().map(CommercialTermVersion::getTermPlanId).distinct().toList();
        List<Long> versionIds = versions.stream().map(CommercialTermVersion::getTermVersionId).toList();
        Map<Long, CommercialTermPlan> plans = commercialTermPlanRepository.findByIds(planIds).stream()
            .collect(Collectors.toMap(CommercialTermPlan::getTermPlanId, Function.identity()));
        Map<Long, Long> changeVersions = businessChangeRecordRepository.findRootVersions(
            BusinessChangeRecordAggregateRootType.COMMERCIAL_TERM_PLAN, planIds);
        Map<Long, Integer> priceCounts = commercialTermPriceRepository.countByVersionIds(versionIds);
        Map<Long, CommercialTermReviewFacts> reviews = reviewFacts(versionIds);
        Map<Long, CommercialTermVersionInsight> insights = commercialTermInsightService.summarize(versions);
        List<CommercialTermVersionSummaryView> items = versions.stream().map(version -> new CommercialTermVersionSummaryView(
            plans.get(version.getTermPlanId()), version, changeVersions.get(version.getTermPlanId()),
            priceCounts.getOrDefault(version.getTermVersionId(), 0),
            reviews.getOrDefault(version.getTermVersionId(), CommercialTermReviewFacts.NONE),
            insights.getOrDefault(version.getTermVersionId(), CommercialTermVersionInsight.NONE))).toList();
        return new PagingResponseBody<>(items, query.getPaging().page(), query.getPaging().size(), total);
    }

    /**
     * 读取各版本 Child 的全部快照并推导本轮审批事实
     *
     * @param versionIds 当前页版本 ID
     * @return 版本 ID → 审批事实；没有 Child 的版本不出现
     */
    Map<Long, CommercialTermReviewFacts> reviewFacts(List<Long> versionIds) {
        List<BusinessChangeRecord> children = businessChangeRecordRepository.findByRecords(
            BusinessChangeRecordRecordType.COMMERCIAL_TERM_VERSION, versionIds.stream().map(String::valueOf).toList());
        Map<Long, List<BusinessChangeVersion>> snapshots = businessChangeVersionRepository
            .findByHistoryIds(children.stream().map(BusinessChangeRecord::getHistoryId).toList()).stream()
            .collect(Collectors.groupingBy(BusinessChangeVersion::getHistoryId));
        return children.stream().collect(Collectors.toMap(child -> Long.valueOf(child.getRecordId()),
            child -> CommercialTermReviewFacts.from(snapshots.getOrDefault(child.getHistoryId(), List.of()))));
    }
}
