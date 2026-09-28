package com.paytech.risk.compliance.domainservice.repository.impl;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.paytech.risk.compliance.core.dal.dataobject.*;
import com.paytech.risk.compliance.core.dal.mapper.MaterialRequirementsMapper;
import com.paytech.risk.compliance.domainservice.repository.MaterialRequirementsRepository;
/** 同库 MyBatis 适配，不创建或修复材料规则。 */
@Repository
public class MaterialRequirementsRepositoryImpl implements MaterialRequirementsRepository {
    private final MaterialRequirementsMapper mapper;
    /** 注入本 Owner 的持久化接口。 */
    public MaterialRequirementsRepositoryImpl(MaterialRequirementsMapper mapper) { this.mapper=mapper; }
    /** 不以默认程序替代未配置程序。 */
    @Override public List<MaterialProgram> programs(String code,String qualificationType) { return mapper.programs(code,qualificationType); }
    /** 不合并不同程序修订。 */
    @Override public List<MaterialRequirement> requirements(long versionId) { return mapper.requirements(versionId); }
}
