package com.paytech.client.master.service.processor;

import java.time.Duration;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.CommandReceiptResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartApplicationRequestBody;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.CreateClientApplicationRequest;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

/**
 * C02 开始新申请：为账号分配唯一的未完成申请名额并创建草稿。
 * <li>按账号名额 client-onboarding:account-slot:{accountId} 串行；Platform 会话在事务外核验。</li>
 * <li>Redis 租约失效时由账号未完成名额唯一索引回滚竞争事务，重试后返回胜出的申请。</li>
 * <li>新建返回 CREATED 回执，账号已有未完成申请时返回原申请。</li>
 */
public class CreateClientApplicationProcessor
    extends AbstractLockProcessor<CreateClientApplicationRequest, CommandReceiptResponseBody> {
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientDraftService service;
    private final ClientDraftAccountClient accounts;
    private final TransactionTemplate writes;

    /** 锁等待与租约沿用 CCT 既有注解锁配置，保持同一互斥域。 */
    public CreateClientApplicationProcessor(Duration lockWait, Duration lockLease, ClientDraftService service,
        ClientDraftAccountClient accounts, TransactionTemplate writes) {
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.service = service;
        this.accounts = accounts;
        this.writes = writes;
    }

    /** 与原注解锁 client-onboarding:account-slot 生成同一逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(CreateClientApplicationRequest request) {
        return LockCommand.tryLocks(List.of(ClientOnboardingLocks.accountSlot(request.getAccountId())), lockWait,
            lockLease);
    }

    @Override
    protected CommandReceiptResponseBody processWithinLock(CreateClientApplicationRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        ClientType type = clientType(request.getBody());
        var command = ClientDraftCommand.of(request.getAccountId(), "CLIENT_APPLICATION_CREATE");
        return ClientOnboardingCommands.retryActiveSlotCollision(() -> writes.execute(tx -> service.create(command, type)));
    }

    /** 申请类型只接受闭合目录内的 Code；空值或目录外取值以 clientType 字段错误拒绝。 */
    static ClientType clientType(StartApplicationRequestBody request) {
        String code = request == null ? null : request.clientType();
        for (ClientType type : ClientType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new RequestBodyValidationException(INVALID_DRAFT,
            List.of(new ProblemFieldError("clientType", "须为 INDIVIDUAL 或 ORGANIZATION")), null);
    }
}
