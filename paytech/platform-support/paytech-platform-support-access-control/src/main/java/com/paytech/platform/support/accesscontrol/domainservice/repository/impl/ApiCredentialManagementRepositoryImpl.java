package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiCredentialManagement;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ApiCredentialManagementMapper;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialDetailView;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialSummaryView;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.CredentialFilter;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiCredentialManagementRepository;

/** 封装本库查询与条件更新，检查影响行数并回写调用方持有的原对象。 */
@Repository
@lombok.AllArgsConstructor
public class ApiCredentialManagementRepositoryImpl implements ApiCredentialManagementRepository {
    private final ApiCredentialManagementMapper mapper;

    /** 按身份读取 Client 凭证的非秘密详情，保留所属 Tenant 状态。 */
    @Override
    public ApiCredentialDetailView credential(long id) {
        return mapper.credential(id);
    }

    /** 在调用方本库事务中锁定目标行，供版本校验及条件更新使用。 */
    @Override
    public ApiCredentialManagement lock(long id) {
        return mapper.lock(id);
    }

    /** 分页查询 Client 凭证的非秘密信息，过滤与总数使用同一口径。 */
    @Override
    public List<ApiCredentialSummaryView> credentials(CredentialFilter filter, Instant asOf, long offset, int size) {
        return mapper.credentials(filter, asOf, offset, size);
    }

    /** 使用与列表相同的筛选条件统计总数。 */
    @Override
    public long count(CredentialFilter filter, Instant asOf) {
        return mapper.count(filter, asOf);
    }

    /** 更新成功后同步原对象；失败不回写，事务回滚后由调用方丢弃本次 Context。 */
    @Override
    public void revoke(ApiCredentialManagement credential, Instant now) {
        int previousVersion = credential.getVersion();
        if (previousVersion == Integer.MAX_VALUE
            || mapper.revoke(credential.getCredentialId(), previousVersion, now) != 1) {
            throw new ConcurrentRequestException("凭证已被其他操作修改，请刷新后重试");
        }
        credential.setStoredStatus(ApiCredentialStatus.REVOKED);
        credential.setVersion(previousVersion + 1);
        credential.setUpdateTime(now);
    }
}
