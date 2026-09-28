package com.paytech.client.contract.integration.impl;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.client.contract.integration.ClientDraftAccountClient;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.foundation.common.error.*;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 不缓存账号许可；在任何 CCT 本地事务开始前重新读取会话事实。 */
public final class ClientDraftAccountClientImpl implements ClientDraftAccountClient {
    private final ClientAuthenticationApi api;
    public ClientDraftAccountClientImpl(ClientAuthenticationApi api) { this.api=api; }
    public void authorize(long accountId,long sessionId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Platform 调用不能位于数据库事务内");
        try {
            var context=api.context(Long.toString(accountId),Long.toString(sessionId));
            if (context==null || !Long.toString(accountId).equals(context.getAccountId()) || !Long.toString(sessionId).equals(context.getSessionId())
                || !context.isEmailVerified() || context.getContextType()==null || !Set.of("ACCOUNT","TENANT").contains(context.getContextType()))
                throw new FailException(CLIENT_SESSION_INVALID);
        } catch (FailException rejected) {
            if ("CLIENT_SESSION_INVALID".equals(rejected.getCode())) throw new FailException(CLIENT_SESSION_INVALID);
            throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE);
        } catch (RuntimeException unavailable) { throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE); }
    }
}
