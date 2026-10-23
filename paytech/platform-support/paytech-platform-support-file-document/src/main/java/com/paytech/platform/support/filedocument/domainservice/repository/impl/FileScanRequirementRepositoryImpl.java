package com.paytech.platform.support.filedocument.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicyRequirement;
import com.paytech.platform.support.filedocument.core.dal.mapper.FileScanRequirementMapper;
import com.paytech.platform.support.filedocument.domainservice.repository.FileScanRequirementRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的检查要求仓储。 */
@Repository
@AllArgsConstructor
public class FileScanRequirementRepositoryImpl implements FileScanRequirementRepository {

    private final FileScanRequirementMapper mapper;

    /** 空入参不发 SQL。 */
    @Override
    public List<FileScanPolicyRequirement> findByPolicyIds(List<Long> policyIds) {
        return policyIds.isEmpty() ? List.of() : mapper.selectByPolicyIds(policyIds);
    }

    /** 插入检查要求。 */
    @Override
    public void insert(FileScanPolicyRequirement requirement) {
        if (mapper.insert(requirement) != 1) {
            throw new ConcurrentRequestException("检查要求保存失败: " + requirement.getPolicyId());
        }
    }

    /** 删除修订的全部检查要求。 */
    @Override
    public void deleteByPolicy(long policyId) {
        mapper.deleteByPolicy(policyId);
    }
}
