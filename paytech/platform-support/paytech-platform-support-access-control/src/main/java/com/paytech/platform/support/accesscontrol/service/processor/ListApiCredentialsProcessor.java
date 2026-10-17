package com.paytech.platform.support.accesscontrol.service.processor;

import static com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementPermissions.CREDENTIAL_READ;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialSummaryView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiCredentialManagementRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListApiCredentialsRequest;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** Operations 凭证列表用例：在同一只读快照内复核权限并分页查询。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListApiCredentialsProcessor
    extends AbstractProcessor<ListApiCredentialsRequest, PagingResponseBody<ApiCredentialSummaryView>> {

    private final Clock clock;
    private final OperationsAuditService audit;
    private final OperationsSessionService sessions;
    private final OperationsDirectoryRepository directory;
    private final ApiCredentialManagementRepository repository;
    private final PlatformTransactionManager transactionManager;

    /** 保留管理查询原有的 REPEATABLE READ、只读和 15 秒超时合同。 */
    @Override
    protected PagingResponseBody<ApiCredentialSummaryView> processRequest(ListApiCredentialsRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(15);
        return transaction.execute(status -> query(request));
    }

    PagingResponseBody<ApiCredentialSummaryView> query(ListApiCredentialsRequest request) {
        audit.observeTransaction();
        authorize(request);
        Instant asOf = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var paging = request.getPaging();
        List<ApiCredentialSummaryView> items =
            repository.credentials(request.getFilter(), asOf, paging.offset(), paging.size());
        for (ApiCredentialSummaryView item : items) {
            if (item == null) {
                throw new FailException(CommonErrorCode.NOT_FOUND);
            }
            item.prepareForDisplay(asOf);
        }
        long total = repository.count(request.getFilter(), asOf);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    void authorize(ListApiCredentialsRequest request) {
        var current = sessions.requireCurrent(request.getActor(), request.getSession());
        sessions.requireOrdinary(current);
        if (!directory.permissions(current.member().id()).contains(CREDENTIAL_READ)) {
            throw new FailException(OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        }
    }

}
