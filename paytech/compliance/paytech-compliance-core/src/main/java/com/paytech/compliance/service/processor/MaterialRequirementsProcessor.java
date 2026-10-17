package com.paytech.compliance.service.processor;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.compliance.core.dal.dataobject.MaterialProgram;
import com.paytech.compliance.core.dal.dataobject.MaterialRequirement;
import com.paytech.compliance.domainservice.policy.MaterialProgramResolver;
import com.paytech.compliance.domainservice.repository.MaterialRequirementsRepository;
import com.paytech.compliance.service.processor.request.MaterialRequirementsRequest;
import com.paytech.compliance.service.processor.response.MaterialRequirementsResponse;

import lombok.AllArgsConstructor;

/**
 * 按资格类型读取当前批准的通用材料目录
 * <li>先由配置解析程序编码，再在同一只读可重复读快照内读取唯一生效修订及其材料要求，避免混合两次提交。</li>
 * <li>无映射、程序缺失或歧义、修订未批准/未生效、要求为空/超限/损坏一律 503，不默认无需材料，也不创建 Case 或代表审核通过。</li>
 * <li>数据库访问失败按合同同样返回 503，供调用方识别为暂时不可用并 Fail-Closed。</li>
 */
@Component
@AllArgsConstructor
public class MaterialRequirementsProcessor extends AbstractProcessor<MaterialRequirementsRequest, MaterialRequirementsResponse> {
    /** 单个修订允许的最大材料要求数。 */
    static final int MAX_REQUIREMENTS = 1024;

    private final Clock clock;
    private final MaterialProgramResolver resolver;
    private final PlatformTransactionManager transactionManager;
    private final MaterialRequirementsRepository repository;

    @Override
    protected MaterialRequirementsResponse processRequest(MaterialRequirementsRequest request) {
        String programCode = resolver.resolve(request.getQualificationType());
        try {
            return snapshot().execute(status -> readCatalog(programCode, request.getQualificationType().getCode()));
        } catch (DataAccessException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
    }

    /** 读取唯一生效修订并校验完整材料闭包。 */
    MaterialRequirementsResponse readCatalog(String programCode, String qualificationType) {
        List<MaterialProgram> candidates = repository.programs(programCode, qualificationType);
        if (candidates.size() != 1 || !candidates.getFirst().isApprovedAuthorityAt(clock.instant())) {
            throw new ServiceUnavailableException();
        }
        MaterialProgram program = candidates.getFirst();
        List<MaterialRequirement> rows = repository.requirements(program.getProgramVersionId());
        if (rows.isEmpty() || rows.size() > MAX_REQUIREMENTS) {
            throw new ServiceUnavailableException();
        }
        return new MaterialRequirementsResponse(program.getProgramId(), program.getProgramVersionId(), items(rows));
    }

    /** 每条要求必须格式正确且代码不重复。 */
    List<MaterialRequirementsResponse.Item> items(List<MaterialRequirement> rows) {
        Set<String> codes = new HashSet<>();
        for (MaterialRequirement row : rows) {
            if (!row.isWellFormed() || !codes.add(row.getRequirementCode())) {
                throw new ServiceUnavailableException();
            }
        }
        return rows.stream().map(row -> new MaterialRequirementsResponse.Item(row.getRequirementCode(),
            row.getDocumentType(), row.isRequired())).toList();
    }

    /** 只读可重复读快照，不持有排他锁。 */
    TransactionTemplate snapshot() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }
}
