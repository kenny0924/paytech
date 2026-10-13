package com.paytech.risk.compliance.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.risk.compliance.service.processor.request.ListClientReviewCasesRequest;
import com.paytech.risk.compliance.service.processor.response.ClientReviewCasePageResponse;

import lombok.AllArgsConstructor;

/** O01：Client 入驻主案件分页；只读，不创建显式事务，按提交时间倒序稳定排序。 */
@Component
@AllArgsConstructor
public class ListClientReviewCasesProcessor extends AbstractProcessor<ListClientReviewCasesRequest, ClientReviewCasePageResponse> {
    private final DueDiligenceCaseRepository cases;

    @Override
    protected ClientReviewCasePageResponse processRequest(ListClientReviewCasesRequest request) {
        long offset = (long)(request.getPage() - 1) * request.getSize();
        var items = cases.pageClientCases(request.getStatus(), request.getQualificationType(), request.getApplicationId(), offset,
            request.getSize()).stream().map(ClientReviewCaseConverter::summary).toList();
        long total = cases.countClientCases(request.getStatus(), request.getQualificationType(), request.getApplicationId());
        return new ClientReviewCasePageResponse(items, request.getPage(), request.getSize(), total);
    }
}
