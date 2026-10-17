package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.release.ExecutionReleasePageView;
import com.paytech.risk.decision.service.processor.request.ListExecutionReleasesRequest;
import com.paytech.risk.decision.service.query.ExecutionReleaseListReader;

import lombok.AllArgsConstructor;

/**
 * 执行发布分页列表
 * <li>只列经决策流的执行发布，不含结账测试候选；总数与当前页在同一只读快照中读取。</li>
 * <li>不提供重新激活已替代发布：回滚以旧决策流版本新建执行发布。</li>
 */
@Component
@AllArgsConstructor
public class ListExecutionReleasesProcessor extends AbstractProcessor<ListExecutionReleasesRequest, ExecutionReleasePageView> {
    private final ExecutionReleaseListReader reader;
    private final PlatformTransactionManager transactionManager;

    @Override
    protected ExecutionReleasePageView processRequest(ListExecutionReleasesRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> reader.read(request.getPointCode(), request.getStatus(), request.getPage(),
            request.getSize()));
    }
}
