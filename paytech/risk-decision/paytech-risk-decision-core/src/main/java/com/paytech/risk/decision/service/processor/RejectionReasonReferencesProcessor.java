package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.domain.query.DecisionConfigurationQueries.ReasonReferenceView;
import com.paytech.risk.decision.domain.query.RejectionReasonReferencesContext;
import com.paytech.risk.decision.domainservice.repository.RuleRejectReasonRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationQueryService;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException.Reason;
import com.paytech.risk.decision.service.processor.request.RejectionReasonReferencesRequest;

import lombok.AllArgsConstructor;

/** 编排字典存在性、不可变引用和当前发布事实的同快照查询。 */
@Component
@AllArgsConstructor
public class RejectionReasonReferencesProcessor
    extends AbstractProcessor<RejectionReasonReferencesRequest, PagingResponseBody<ReasonReferenceView>> {
    private final PlatformTransactionManager transactionManager;
    private final RuleRejectReasonRepository repository;
    private final DecisionConfigurationQueryService service;

    /** 字典、引用计数、当前页及活动发布必须来自同一快照，避免并发保存产生矛盾使用关系。 */
    @Override
    protected PagingResponseBody<ReasonReferenceView> processRequest(RejectionReasonReferencesRequest request) {
        var paging = PagingRequestBody.of(request.getPage(), request.getSize());
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> readReferences(request, paging));
    }

    /** 允许停用字典反查历史，确认对象完整后组合多表 View。 */
    PagingResponseBody<ReasonReferenceView> readReferences(RejectionReasonReferencesRequest request,
        PagingRequestBody paging) {
        var value = repository.findByCode(request.getReasonCode())
            .orElseThrow(() -> new DictionaryConflictException(Reason.NOT_FOUND));
        value.verifyIntegrity();
        var context = new RejectionReasonReferencesContext(value, paging.page(), paging.size(),
            request.getScope() == null ? "ALL" : request.getScope());
        return service.reasonReferences(context);
    }
}
