package com.paytech.compliance.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.service.processor.request.ListClientReviewCasesRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCasePageResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** O01 审核案件列表；按责任 PayFac、辖区等过滤（本期运营全可见）。 */
@Component
@AllArgsConstructor
public class ListClientReviewCasesProcessor extends AbstractProcessor<ListClientReviewCasesRequest, ClientReviewCasePageResponse> {
    private final DueDiligenceCaseRepository cases;

    /** 数据库分页与计数使用相同条件。 */
    @Override
    protected ClientReviewCasePageResponse processRequest(ListClientReviewCasesRequest request) {
        long offset = (long)(request.getPage() - 1) * request.getSize();
        var items = cases.pageClientCases(request.getStatus(), request.getQualificationType(), request.getApplicationId(),
            request.getResponsiblePartnerId(), request.isPlatformOnly(), request.getJurisdictionCode(), offset, request.getSize())
            .stream().map(ClientReviewCaseConverter::summary).toList();
        long total = cases.countClientCases(request.getStatus(), request.getQualificationType(), request.getApplicationId(),
            request.getResponsiblePartnerId(), request.isPlatformOnly(), request.getJurisdictionCode());
        return new ClientReviewCasePageResponse(items, request.getPage(), request.getSize(), total);
    }
}
