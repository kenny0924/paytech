package com.paytech.platform.support.accesscontrol.service.processor;

import static com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementPermissions.CREDENTIAL_READ;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialDetailView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiCredentialManagementRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetApiCredentialRequest;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 凭证管理查询：鉴权与关联读取在同一个只读快照内完成。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetApiCredentialProcessor extends AbstractProcessor<GetApiCredentialRequest, ApiCredentialDetailView> {

    private final Clock clock;
    private final PlatformTransactionManager transactionManager;
    private final OperationsSessionService sessions;
    private final OperationsDirectoryRepository directory;
    private final ApiCredentialManagementRepository repository;
    private final OperationsAuditService audit;

    /** 保留管理查询的只读快照及 15 秒超时，避免权限与关联数据混用不同提交时点。 */
    @Override
    protected ApiCredentialDetailView processRequest(GetApiCredentialRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(15);
        return transaction.execute(status -> query(request));
    }

    /** 完成当前普通会话与读取权限校验，再从本模块读取非秘密数据。 */
    ApiCredentialDetailView query(GetApiCredentialRequest request) {
        audit.observeTransaction();
        authorize(request);
        Instant asOf = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ApiCredentialDetailView credential = repository.credential(request.getCredentialId());
        if (credential == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        credential.prepareForDisplay(asOf);
        if (credential.getRotatedFromId() != null) {
            credential.validateRotationSource(repository.credential(credential.getRotatedFromId()));
        }
        return credential;
    }

    /** 复用当前会话规则，独立检查凭证读取权限。 */
    void authorize(GetApiCredentialRequest request) {
        var current = sessions.requireCurrent(request.getActor(), request.getSession());
        sessions.requireOrdinary(current);
        if (!directory.permissions(current.member().id()).contains(CREDENTIAL_READ)) {
            throw new FailException(OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        }
    }
}
