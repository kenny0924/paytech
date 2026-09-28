package com.paytech.risk.service.processor;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.risk.api.compliance.*;
import com.paytech.risk.api.enums.MaterialQualificationType;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.*;
import com.paytech.risk.compliance.domainservice.service.MaterialRequirementsService;
import com.paytech.risk.configuration.MaterialProgramResolver;
import com.paytech.risk.service.transaction.RiskTransactionExecutor;
/** 映射在查询前确定，程序与要求在一个无写锁的本地快照读取。 */
@Component @lombok.AllArgsConstructor
@ConditionalOnProperty(name="onboarding.materials.enabled",havingValue="true")
public class MaterialRequirementsProcessor {
    private final MaterialProgramResolver resolver;
    private final MaterialRequirementsService service;
    private final RiskTransactionExecutor transactions;
    private final Clock clock;
    /** 不创建审核任务或把目录存在解释为资格通过。 */
    public RequirementsResponseBody read(MaterialQualificationType type,String country) {
        String program=resolver.resolve(type,country);
        try {
            var result=transactions.read(() -> service.read(program,type.getCode(),clock.instant()));
            return new RequirementsResponseBody(Long.toString(result.programId()),Long.toString(result.programVersionId()),
                result.items().stream().map(item -> new RequirementBody(item.requirementCode(),item.documentType(),item.requiredFlag())).toList());
        } catch (DataAccessException unavailable) { throw new ServiceUnavailableException(); }
    }
}
