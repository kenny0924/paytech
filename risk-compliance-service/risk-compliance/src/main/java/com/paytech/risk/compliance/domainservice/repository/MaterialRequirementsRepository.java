package com.paytech.risk.compliance.domainservice.repository;
import java.util.List;
import com.paytech.risk.compliance.core.dal.dataobject.*;
/** 持久化对象仅在 Compliance 能力模块内部使用。 */
public interface MaterialRequirementsRepository {
    /** 查询准确配置程序的生效候选。 */
    List<MaterialProgram> programs(String code,String qualificationType);
    /** 查询原修订有界材料目录。 */
    List<MaterialRequirement> requirements(long versionId);
}
