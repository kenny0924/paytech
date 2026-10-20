package com.paytech.commercial.service.processor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.BusinessChangeEntryView;
import com.paytech.commercial.domain.BusinessChangeHistoryQuery;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 按聚合根读取变更历史（运营控制台第二切片 technique.md §2）：只读，不加锁、不开显式事务
 * <li>先读聚合根下的全部历史身份（Root 与受审计 Child），再按版本倒序分页读取其快照；聚合根没有历史时返回空页。</li>
 * <li>快照内容按 record_type 白名单生成（BusinessChangeSnapshot），不含凭据或敏感明文；完整性哈希不对外。</li>
 */
@Component
@AllArgsConstructor
public class BusinessChangeHistoryQueryProcessor
    extends AbstractProcessor<BusinessChangeHistoryQuery, PagingResponseBody<BusinessChangeEntryView>> {
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final BusinessChangeVersionRepository businessChangeVersionRepository;

    @Override
    protected PagingResponseBody<BusinessChangeEntryView> processRequest(BusinessChangeHistoryQuery query) {
        List<BusinessChangeRecord> records = businessChangeRecordRepository.findByAggregateRoot(
            query.getAggregateRootType(), Long.toString(query.getAggregateRootId()));
        if (records.isEmpty()) {
            return new PagingResponseBody<>(List.of(), query.getPaging().page(), query.getPaging().size(), 0);
        }
        Map<Long, BusinessChangeRecord> byHistoryId = records.stream()
            .collect(Collectors.toMap(BusinessChangeRecord::getHistoryId, Function.identity()));
        long rootHistoryId = root(records, query).getHistoryId();
        List<BusinessChangeEntryView> items = businessChangeVersionRepository
            .findPageByHistoryIds(byHistoryId.keySet(), rootHistoryId, query.getPaging()).stream()
            .map(snapshot -> new BusinessChangeEntryView(byHistoryId.get(snapshot.getHistoryId()), snapshot)).toList();
        long total = businessChangeVersionRepository.countByHistoryIds(byHistoryId.keySet());
        return new PagingResponseBody<>(items, query.getPaging().page(), query.getPaging().size(), total);
    }

    /**
     * 聚合根的 Root 历史身份：没有父记录的那一条
     *
     * @param records 聚合根下的全部历史身份
     * @param query 查询条件，用于诊断
     * @return Root
     */
    static BusinessChangeRecord root(List<BusinessChangeRecord> records, BusinessChangeHistoryQuery query) {
        return records.stream().filter(record -> record.getParentHistoryId() == null).findFirst()
            .orElseThrow(() -> new ErrorException("聚合缺少 BCR 根: " + query.getAggregateRootType().getCode() + " "
                + query.getAggregateRootId()));
    }
}
