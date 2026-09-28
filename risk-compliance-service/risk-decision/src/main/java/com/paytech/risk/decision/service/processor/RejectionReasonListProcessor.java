package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Entry;
import com.paytech.risk.decision.domainservice.convert.dictionary.RejectionReasonConverter;
import com.paytech.risk.decision.domainservice.repository.RuleRejectReasonRepository;
import com.paytech.risk.decision.service.processor.request.RejectionReasonListRequest;

import lombok.AllArgsConstructor;

/** 在 Decision 模块内查询拒绝原因当前值，复用既有领域结果作为模块边界。 */
@Component
@AllArgsConstructor
public class RejectionReasonListProcessor
    extends AbstractProcessor<RejectionReasonListRequest, PagingResponseBody<Entry>> {

    private final RuleRejectReasonRepository repository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 校验分页后，在同一只读快照中统计并读取当前页。
     * <li>BFF 严格核对页条数与 total；并发创建或启停不能使一次响应中的两者互相矛盾。</li>
     */
    @Override
    protected PagingResponseBody<Entry> processRequest(RejectionReasonListRequest request) {
        PagingRequestBody paging = PagingRequestBody.of(request.getPage(), request.getSize());
        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setReadOnly(true);
        readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return readTransaction.execute(status -> readPage(request, paging));
    }

    /** 对计数和当前页使用相同筛选，空页保留请求的分页坐标。 */
    PagingResponseBody<Entry> readPage(RejectionReasonListRequest request, PagingRequestBody paging) {
        long total = repository.count(request.getKeyword(), request.getStatus());
        var items = repository.findPage(request.getKeyword(), request.getStatus(), paging.size(), paging.offset())
            .stream().map(this::toEntry).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 校验已加载原因的当前事实，仅将既有领域字段传给 Web，不暴露数据库主键。 */
    Entry toEntry(RuleRejectReason reason) {
        reason.verifyIntegrity();
        return RejectionReasonConverter.toEntry(reason);
    }
}
