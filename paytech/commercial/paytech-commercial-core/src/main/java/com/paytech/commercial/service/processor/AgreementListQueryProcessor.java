package com.paytech.commercial.service.processor;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.domain.AgreementListQuery;
import com.paytech.commercial.domain.AgreementSummaryView;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 合同列表（运营控制台第二切片 technique.md §2）：只读，不加锁、不开显式事务
 * <li>当前页与总数是相邻的两次一致性读，允许观察到两者之间提交的写入；每行附带合同 BCR Root 当前版本供后续写入 CAS。</li>
 */
@Component
@AllArgsConstructor
public class AgreementListQueryProcessor
    extends AbstractProcessor<AgreementListQuery, PagingResponseBody<AgreementSummaryView>> {
    private final AgreementRepository agreementRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;

    @Override
    protected PagingResponseBody<AgreementSummaryView> processRequest(AgreementListQuery query) {
        List<Agreement> agreements = agreementRepository.findPage(query);
        long total = agreementRepository.count(query);
        Map<Long, Long> changeVersions = businessChangeRecordRepository.findRootVersions(
            BusinessChangeRecordAggregateRootType.AGREEMENT, agreements.stream().map(Agreement::getAgreementId).toList());
        List<AgreementSummaryView> items = agreements.stream()
            .map(agreement -> new AgreementSummaryView(agreement, changeVersions.get(agreement.getAgreementId()))).toList();
        return new PagingResponseBody<>(items, query.getPaging().page(), query.getPaging().size(), total);
    }
}
