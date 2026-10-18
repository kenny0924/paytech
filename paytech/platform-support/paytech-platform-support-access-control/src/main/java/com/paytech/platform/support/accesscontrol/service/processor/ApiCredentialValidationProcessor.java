package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.TreeSet;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiCredentialEffectiveScope;
import com.paytech.platform.support.accesscontrol.domain.apicredential.ApiCredentialCandidateView;
import com.paytech.platform.support.accesscontrol.domain.apicredential.ApiCredentialIdentityView;
import com.paytech.platform.support.accesscontrol.domain.apicredential.ApiCredentialValidationContext;
import com.paytech.platform.support.accesscontrol.domain.apicredential.ApiCredentialVerifier;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiCredentialRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiCredentialScopeRepository;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;
import com.paytech.platform.support.api.enums.ApiCredentialErrorCode;

import lombok.AllArgsConstructor;

/** PLT-I03 只读认证编排：同一数据库快照查询，事务结束后进行 PHC 校验。 */
@Component
@AllArgsConstructor
public class ApiCredentialValidationProcessor extends AbstractProcessor<String, ApiCredentialIdentityView> {

    private static final int CANDIDATE_QUERY_LIMIT = 2;
    private static final int SCOPE_QUERY_LIMIT = ApiCredentialConstraints.MAX_EFFECTIVE_SCOPE_CODES + 1;
    private static final ApiCredentialVerifier VERIFIER = new ApiCredentialVerifier();

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final ApiCredentialRepository credentialRepository;
    private final ApiCredentialScopeRepository scopeRepository;

    /** 校验唯一 Header 参数；保留缺失或畸形凭证统一返回 401 的特殊认证合同。 */
    @Override
    protected void validateBasic(String credential) {
        if (!ApiCredentialConstraints.isValidCredential(credential)) {
            throw authenticationFailed();
        }
    }

    /** 读取一致性上下文，在事务外验证原始凭证，再构造不含秘密的可信身份。 */
    @Override
    protected ApiCredentialIdentityView processRequest(String credential) {
        String keyPrefix = ApiCredentialConstraints.keyPrefix(credential);
        ApiCredentialValidationContext context = loadContext(keyPrefix);
        verifyCredential(credential, context.getCandidate().getKeyVerifierPhc());
        return buildIdentity(context);
    }

    /**
     * 在同一 REPEATABLE READ 只读事务内加载凭证、租户及授权。
     *
     * <p>两次 SQL 若混用并发提交前后状态，会把旧身份与新授权拼接为从未存在过的权限集合。
     * 数据访问异常可能包含 SQL 参数或 PHC，按认证安全合同仅向外传播固定安全错误。
     */
    ApiCredentialValidationContext loadContext(String keyPrefix) {
        try {
            ApiCredentialValidationContext context = transactionTemplate.execute(status -> buildContext(keyPrefix));
            if (context == null) {
                throw validationUnavailable();
            }
            return context;
        } catch (DataAccessException | TransactionException exception) {
            throw validationUnavailable();
        }
    }

    /** 使用一个查询时点校验凭证及租户，再构造完整且不保存请求秘密的上下文。 */
    ApiCredentialValidationContext buildContext(String keyPrefix) {
        Instant effectiveAt = clock.instant();
        List<ApiCredentialCandidateView> candidates = credentialRepository.candidates(keyPrefix, CANDIDATE_QUERY_LIMIT);
        if (candidates == null || candidates.size() > 1) {
            throw validationUnavailable();
        }
        if (candidates.isEmpty()) {
            throw authenticationFailed();
        }
        ApiCredentialCandidateView candidate = candidates.getFirst();
        validateStoredCandidate(candidate, keyPrefix, effectiveAt);
        List<String> scopeCodes = validatedScopeCodes(candidate.getCredentialId(), effectiveAt);
        return new ApiCredentialValidationContext(candidate, scopeCodes);
    }

    /** 区分损坏的持久化事实与正常失效的凭证或租户，禁止返回部分可信身份。 */
    void validateStoredCandidate(ApiCredentialCandidateView candidate, String expectedKeyPrefix, Instant effectiveAt) {
        if (candidate == null || candidate.getCredentialId() <= 0 || candidate.getTenantId() <= 0
            || candidate.getCredentialVersion() <= 0 || candidate.getTenantVersion() <= 0
            || candidate.getExpiresTime() == null || !expectedKeyPrefix.equals(candidate.getKeyPrefix())
            || candidate.getKeyVerifierPhc() == null || candidate.getKeyVerifierPhc().isBlank()
            || candidate.getKeyVerifierPhc().length() > 512
            || candidate.getOwnerRefId() == null || candidate.getOwnerRefId() <= 0 || candidate.getStatus() == null
            || candidate.getTenantType() == null || candidate.getOwnerStatus() == null
            || candidate.getSecurityStatus() == null) {
            throw validationUnavailable();
        }
        if (candidate.getStatus() != ApiCredentialStatus.ACTIVE || !candidate.getExpiresTime().isAfter(effectiveAt)
            || candidate.getTenantType() != AccessTenantType.CLIENT
            || candidate.getOwnerStatus() != OperationsTenantOwnerStatus.ACTIVE
            || candidate.getSecurityStatus() != OperationsTenantSecurityStatus.ACTIVE) {
            throw authenticationFailed();
        }
    }

    /** 事务结束后执行有界 PHC 计算；损坏校验值使用安全的 503，错误秘密统一 401。 */
    void verifyCredential(String credential, String verifierPhc) {
        try {
            if (!VERIFIER.matches(credential, verifierPhc)) {
                throw authenticationFailed();
            }
        } catch (IllegalStateException exception) {
            throw validationUnavailable();
        }
    }

    /** 验证当前授权的归属、版本、代码与数量，不截断或忽略损坏授权。 */
    List<String> validatedScopeCodes(long credentialId, Instant effectiveAt) {
        List<ApiCredentialEffectiveScope> scopes = scopeRepository.effectiveScopes(
            credentialId, effectiveAt, SCOPE_QUERY_LIMIT);
        if (scopes == null) {
            throw validationUnavailable();
        }
        TreeSet<String> unique = new TreeSet<>();
        for (ApiCredentialEffectiveScope scope : scopes) {
            if (scope == null || scope.getScopeId() <= 0 || scope.getCredentialId() != credentialId
                || scope.getVersion() <= 0 || !ApiCredentialConstraints.isValidScopeCode(scope.getScopeCode())) {
                throw validationUnavailable();
            }
            unique.add(scope.getScopeCode());
            if (unique.size() > ApiCredentialConstraints.MAX_EFFECTIVE_SCOPE_CODES) {
                throw validationUnavailable();
            }
        }
        return List.copyOf(unique);
    }

    /** 从已验证快照组装跨模块身份，不再次查询数据库或暴露含秘密的候选投影。 */
    ApiCredentialIdentityView buildIdentity(ApiCredentialValidationContext context) {
        ApiCredentialCandidateView candidate = context.getCandidate();
        return new ApiCredentialIdentityView(candidate.getTenantId(), AccessTenantType.CLIENT,
            candidate.getOwnerRefId(), candidate.getCredentialId(), context.getScopeCodes());
    }

    /** 对不存在、失效及错误秘密返回同一认证失败分类。 */
    FailException authenticationFailed() {
        return new FailException(ApiCredentialErrorCode.API_CREDENTIAL_AUTHENTICATION_FAILED);
    }

    /** 保留 PLT-I03 专用不可用分类，避免错误及 Cause 向日志传播认证材料。 */
    ServiceUnavailableException validationUnavailable() {
        return new ServiceUnavailableException(ApiCredentialErrorCode.API_CREDENTIAL_VALIDATION_UNAVAILABLE);
    }
}
