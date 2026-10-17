package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;

/** 所有 IAM 查询与锁定留在 Access Control，文件模块不跨模块 JOIN。 */
@Service
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
@lombok.AllArgsConstructor
public class ClientOnboardingAccessServiceImpl implements ClientOnboardingAccessService {
    private final Clock clock;
    private final TenantProjectionRepository repository;
    private final CustomerAuthenticationService authentication;
    /** 账号先于 Tenant 的数据库锁顺序与来源投影保持一致。 */
    @Override public long lockUploadTenant(long accountId, long sessionId, long expectedClientId) {
        repository.lockAccount(accountId);
        authentication.context(accountId, sessionId);
        var candidates = repository.lockUploadCandidates(accountId, clock.instant());
        if (candidates.isEmpty()) throw new ServiceUnavailableException();
        if (candidates.size() != 1 || candidates.getFirst().clientId() != expectedClientId)
            throw new FailException(OnboardingFileErrorCode.FILE_TARGET_CONFLICT);
        return candidates.getFirst().tenantId();
    }
    /** 纯查询校验当前账号与准确原 Tenant，不挑选最新入驻身份。 */
    @Override public void readFile(long accountId,long expectedClientId,long tenantId) {
        var account=repository.fileAccount(accountId);
        if (account==null || account.realm()!=com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm.CUSTOMER
            || account.accountType()!=com.paytech.platform.support.api.enums.OperationsAccountType.HUMAN
            || account.status()!=com.paytech.platform.support.api.enums.OperationsAccountStatus.ACTIVE)
            throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        var candidates=repository.fileReadCandidates(accountId,tenantId,clock.instant());
        if (candidates.size()!=1 || candidates.getFirst().clientId()!=expectedClientId)
            throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
    }
    /** 原申请批准后读取回执使用当前 TENANT_OWNER，权限撤销仍立即生效。 */
    @Override public void lockFileRead(long accountId,long sessionId,long expectedClientId,long tenantId) {
        repository.lockAccount(accountId);authentication.context(accountId,sessionId);
        var candidates=repository.lockFileReadCandidates(accountId,tenantId,clock.instant());
        if (candidates.size()!=1 || candidates.getFirst().clientId()!=expectedClientId)
            throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
    }
}
