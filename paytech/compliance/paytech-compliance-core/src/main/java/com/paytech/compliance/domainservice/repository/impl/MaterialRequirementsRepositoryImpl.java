package com.paytech.risk.compliance.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.risk.compliance.core.dal.dataobject.MaterialProgram;
import com.paytech.risk.compliance.core.dal.dataobject.MaterialRequirement;
import com.paytech.risk.compliance.core.dal.mapper.MaterialRequirementsMapper;
import com.paytech.risk.compliance.domainservice.repository.MaterialRequirementsRepository;

import lombok.AllArgsConstructor;

/** 材料目录三张表的同库只读访问，不创建或修复材料规则。 */
@Repository
@AllArgsConstructor
public class MaterialRequirementsRepositoryImpl implements MaterialRequirementsRepository {
    private final MaterialRequirementsMapper mapper;

    /** 不以默认程序替代未配置程序。 */
    @Override
    public List<MaterialProgram> programs(String code, String qualificationType) {
        return mapper.programs(code, qualificationType);
    }

    /** 不合并不同程序修订。 */
    @Override
    public List<MaterialRequirement> requirements(long versionId) {
        return mapper.requirements(versionId);
    }
}
