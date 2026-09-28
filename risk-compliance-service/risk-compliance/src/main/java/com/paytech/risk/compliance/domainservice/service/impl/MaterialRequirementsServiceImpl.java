package com.paytech.risk.compliance.domainservice.service.impl;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.risk.compliance.domain.MaterialRequirements;
import com.paytech.risk.compliance.domainservice.repository.MaterialRequirementsRepository;
import com.paytech.risk.compliance.domainservice.service.MaterialRequirementsService;
/** 无默认通过策略；缺失、歧义或不完整的已批准闭包均拒绝。 */
@Service
public class MaterialRequirementsServiceImpl implements MaterialRequirementsService {
    private final MaterialRequirementsRepository repository;
    /** 仅注入 Compliance 自有仓储。 */
    public MaterialRequirementsServiceImpl(MaterialRequirementsRepository repository) { this.repository=repository; }
    /** 只接受唯一、已生效且确有材料要求的批准修订。 */
    @Override public MaterialRequirements read(String programCode,String qualificationType,Instant now) {
        var candidates=repository.programs(programCode,qualificationType);
        if (candidates.size()!=1) throw new ServiceUnavailableException();
        var program=candidates.getFirst();
        if (program.programId()<=0 || program.programVersionId()<=0 || program.versionNo()<1 || !Integer.valueOf(1).equals(program.activeSlot())
            || program.approvedBy()==null || program.approvedBy()<=0 || program.approvedTime()==null || program.approvedTime().isAfter(now)
            || program.contentHash()==null || !program.contentHash().matches("[0-9a-f]{64}")) throw new ServiceUnavailableException();
        var rows=repository.requirements(program.programVersionId());
        if (rows.isEmpty() || rows.size()>1024) throw new ServiceUnavailableException();
        var codes=new HashSet<String>();var items=new ArrayList<MaterialRequirements.Item>();
        for (var row:rows) {
            if (row.requirementCode()==null || !row.requirementCode().matches("[A-Z][A-Z0-9_]{0,63}") || !codes.add(row.requirementCode())
                || row.documentType()==null || !row.documentType().matches("[A-Z][A-Z0-9_]{0,63}") || (row.requiredFlag()!=0 && row.requiredFlag()!=1))
                throw new ServiceUnavailableException();
            items.add(new MaterialRequirements.Item(row.requirementCode(),row.documentType(),row.requiredFlag()==1));
        }
        return new MaterialRequirements(program.programId(),program.programVersionId(),List.copyOf(items));
    }
}
