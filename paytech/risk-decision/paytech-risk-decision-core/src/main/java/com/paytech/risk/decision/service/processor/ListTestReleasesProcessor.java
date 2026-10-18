package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.release.DecisionReleaseAssembler;
import com.paytech.risk.decision.domain.release.DecisionReleaseQueryViews;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ReleasePageView;
import com.paytech.risk.decision.service.processor.request.ListTestReleasesRequest;
import com.paytech.risk.decision.service.query.DecisionReleaseReader;

import lombok.AllArgsConstructor;

/** TEST Candidate 只读查询的 Owner 编排。 */
@Component
@AllArgsConstructor
public class ListTestReleasesProcessor extends AbstractProcessor<ListTestReleasesRequest, ReleasePageView> {
    private final DecisionReleaseReader reader;
    private final DecisionReleaseAssembler assembler;
    private final PlatformTransactionManager transactionManager;

    /** 在同一只读快照中批量组装列表，保持原分页顺序。 */
    @Override
    protected ReleasePageView processRequest(ListTestReleasesRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> {
            int offset;
            try {
                offset = Math.multiplyExact(request.getPage() - 1, request.getSize());
            } catch (ArithmeticException failure) {
                throw new InvalidParameterException("分页范围过大", failure);
            }
            var stored = reader.findPage(new DecisionReleaseQueryViews.ReleasePageQuery(request.getPointCode(),
                request.getStatus(), request.getSize(), offset));
            if (stored.total() < 0 || stored.items().size() > request.getSize()
                || stored.total() < stored.items().size()) {
                throw new ErrorException("Release 分页结果损坏");
            }
            return new ReleasePageView(stored.items().stream().map(assembler::summary).toList(), request.getPage(),
                request.getSize(), stored.total());
        });
    }
}
