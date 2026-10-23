package com.paytech.platform.support.filedocument.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicyRequirement;

/** t_file_scan_requirement 的 SQL 映射；SQL 位于 FileScanRequirementMapper.xml。 */
public interface FileScanRequirementMapper {

    /** 指定修订的全部检查要求，按修订与序号排序。 */
    List<FileScanPolicyRequirement> selectByPolicyIds(@Param("policyIds") List<Long> policyIds);

    /** 插入检查要求。 */
    int insert(@Param("row") FileScanPolicyRequirement row);

    /** 删除修订的全部检查要求。 */
    int deleteByPolicy(@Param("policyId") long policyId);
}
