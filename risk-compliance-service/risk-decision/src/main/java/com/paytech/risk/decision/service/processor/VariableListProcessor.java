package com.paytech.risk.decision.service.processor;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.variable.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.domainservice.repository.RiskVariableMemberRepository;
import com.paytech.risk.decision.domainservice.service.*;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.service.processor.request.*;
import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.*;
import static com.paytech.risk.decision.domainservice.policy.VariableQueryPolicy.pattern;
import static com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.*;
import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import com.paytech.risk.decision.domainservice.policy.VariableQueryPolicy;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.processor.AbstractProcessor;

/** 在一致快照内完成单一变量查询。 */
@Component
@AllArgsConstructor
public class VariableListProcessor extends AbstractProcessor<VariableListRequest, VariableListView> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final PlatformTransactionManager transactionManager;

    /** 只读 RR 覆盖根、关联事实及本页扫描边界。 */
    @Override protected VariableListView processRequest(VariableListRequest request) {
        var query = VariableQueryPolicy.query(request.getKeyword(), request.getSourceType(), request.getValueType(),
            request.getStatus(), request.getPointCode(), request.getUsage(), request.getNodeType(), request.getCodes(), request.getPage(), request.getSize());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> {
            return new VariableListView(reader.list(query, clock.instant().truncatedTo(ChronoUnit.MICROS)),
                query.codes() == null ? query.page() : null, query.codes() == null ? query.size() : null);
        });
    }
}
