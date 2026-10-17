package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.variable.VariableListView;
import com.paytech.risk.decision.domainservice.policy.VariableQueryPolicy;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.service.processor.request.VariableListRequest;

import lombok.AllArgsConstructor;

/**
 * 分页查询或按编码批取变量目录
 * <li>根、集合预览与分页总数在同一只读可重复读快照内读取，避免当前值投影混合两次提交。</li>
 * <li>编码批取不返回页码与每页数量。</li>
 */
@Component
@AllArgsConstructor
public class VariableListProcessor extends AbstractProcessor<VariableListRequest, VariableListView> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final PlatformTransactionManager transactionManager;

    @Override
    protected VariableListView processRequest(VariableListRequest request) {
        var query = VariableQueryPolicy.query(request.getKeyword(), request.getSourceType(), request.getValueType(),
            request.getStatus(), request.getPointCode(), request.getUsage(), request.getNodeType(), request.getCodes(),
            request.getPage(), request.getSize());
        var page = snapshot().execute(status -> reader.list(query, clock.instant().truncatedTo(ChronoUnit.MICROS)));
        boolean batch = query.codes() != null;
        return new VariableListView(page, batch ? null : query.page(), batch ? null : query.size());
    }

    /** 只读可重复读快照。 */
    TransactionTemplate snapshot() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }
}
