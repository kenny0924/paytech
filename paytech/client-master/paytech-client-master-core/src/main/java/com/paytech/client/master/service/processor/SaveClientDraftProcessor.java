package com.paytech.client.master.service.processor;

import java.time.Duration;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.CommandReceiptResponseBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domain.onboarding.ClientDraftPatch;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.integration.ClientDraftContactClient;
import com.paytech.client.master.service.processor.request.SaveClientDraftRequest;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import jakarta.validation.Validator;

/**
 * 保存申请草稿：按 dirtyFields 规范化后保存字段、证件、扩展属性与联系方式。
 * <li>按申请 client-onboarding:application:{applicationId} 串行，与人员、材料写入及正式提交共用互斥域。</li>
 * <li>字段级规范化失败返回 INVALID_DRAFT 及 errors[]；会话核验先于规范化。</li>
 * <li>只读准备事务校验可编辑状态；随后在事务外按辖区包规范化证件号并 Protect（个人证件）、保护联系方式；最终短写事务保存。</li>
 */
public class SaveClientDraftProcessor extends AbstractLockProcessor<SaveClientDraftRequest, CommandReceiptResponseBody> {
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientDraftService service;
    private final ClientDraftAccountClient accounts;
    private final ClientDraftContactClient contacts;
    private final ClientDraftPreparer preparer;
    private final Validator validator;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;

    /** 锁等待与租约沿用 CCT 既有注解锁配置，保持同一互斥域。 */
    public SaveClientDraftProcessor(Duration lockWait, Duration lockLease, ClientDraftService service,
        ClientDraftAccountClient accounts, ClientDraftContactClient contacts, ClientDraftPreparer preparer, Validator validator,
        TransactionTemplate writes, TransactionTemplate reads) {
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.service = service;
        this.accounts = accounts;
        this.contacts = contacts;
        this.preparer = preparer;
        this.validator = validator;
        this.writes = writes;
        this.reads = reads;
    }

    /** 与原注解锁 client-onboarding:application 生成同一逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(SaveClientDraftRequest request) {
        return LockCommand.tryLocks(List.of(ClientOnboardingLocks.application(request.getApplicationId())), lockWait,
            lockLease);
    }

    /** 只读准备 → 事务外准备与 Vault → 短写事务。 */
    @Override
    protected CommandReceiptResponseBody processWithinLock(SaveClientDraftRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        var patch = ClientDraftPatch.normalize(request.getPatch(), validator);
        var command = ClientDraftCommand.of(request.getAccountId(), "CLIENT_DRAFT_SAVE");
        long applicationId = request.getApplicationId();
        var source = reads.execute(tx -> service.prepareSave(command, applicationId, patch));
        var changes = preparer.prepare(source.identity(), source.fields(), patch, source.identity().clientType(), true);
        var protectedContacts = contacts.protect(patch);
        return ClientOnboardingCommands.retryActiveSlotCollision(
            () -> writes.execute(tx -> service.save(command, applicationId, changes, protectedContacts)));
    }
}
