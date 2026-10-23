package com.paytech.platform.support.filedocument.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicyRequirement;

/** 检查要求表仓储。 */
public interface FileScanRequirementRepository {

    /** 指定修订的全部检查要求；入参为空时返回空列表。 */
    List<FileScanPolicyRequirement> findByPolicyIds(List<Long> policyIds);

    /** 插入检查要求，参与调用方事务。 */
    void insert(FileScanPolicyRequirement requirement);

    /** 删除修订的全部检查要求。 */
    void deleteByPolicy(long policyId);
}
