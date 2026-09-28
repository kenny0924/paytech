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

/** 返回工程真实支持的变量编排能力，无数据库或外部调用。 */
@Component
public class VariableAuthoringContextProcessor extends AbstractProcessor<VariableAuthoringContextRequest, VariableAuthoringCapabilities> {
    /** 精确按作用域筛选合同，不以最新版本或固定变量名单替代。 */
    @Override protected VariableAuthoringCapabilities processRequest(VariableAuthoringContextRequest request) {
        String point = request.getPointCode();
        if (point != null && !DecisionSourceContracts.supports(point)) throw new InvalidParameterException("决策点尚未支持");
        return new VariableAuthoringCapabilities(DecisionSourceContracts.all().stream().map(c -> c.pointCode()).distinct().toList(),
            DecisionSourceContracts.all().stream().filter(c -> point == null || point.equals(c.pointCode())).toList(),
            List.of(VariableSource.values()), Arrays.stream(VariableSource.values()).filter(v -> v != VariableSource.NODE_OUTPUT).toList(),
            List.of(DecisionValueType.values()), List.of(VariableFormat.values()), List.of(NumericUnit.values()), 1000, 65536, 100);
    }
}
