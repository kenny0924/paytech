package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.RuleSubjectTag;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Content;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.WriteResult;
import com.paytech.risk.decision.domain.dictionary.SubjectTagChangeContext;
import com.paytech.risk.decision.domain.enums.DictionaryType;
import com.paytech.risk.decision.domainservice.repository.RuleSubjectTagRepository;
import com.paytech.risk.decision.domainservice.service.DecisionDictionaryService;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException.Reason;
import com.paytech.risk.decision.service.processor.request.ChangeSubjectTagStatusRequest;

import lombok.AllArgsConstructor;

/** 按永久标签编码协调变更，单次事务保存当前事实及构造本次结果。 */
@Component
@AllArgsConstructor
public class ChangeSubjectTagStatusProcessor extends AbstractLockProcessor<ChangeSubjectTagStatusRequest, WriteResult> {

    private final Clock clock;
    private final RuleSubjectTagRepository repository;
    private final DecisionDictionaryService service;
    private final PlatformTransactionManager transactionManager;
    private final RedisResourceLockProperties lockProperties;

    /**
     * 与现有字典编辑、启停共用完整逻辑 Key、普通 RLock 和有限等待/租约。
     * <li>迁移使用完整 Key 工厂，避免单锁工厂添加 lock: 后改变原互斥域。</li>
     */
    @Override
    protected LockCommand lockCommand(ChangeSubjectTagStatusRequest request) {
        return LockCommand.tryLocks(List.of("decision:dictionary:SUBJECT_TAG:" + request.getTagCode()),
            Duration.ofNanos(lockProperties.requiredWaitNanos()),
            Duration.ofNanos(lockProperties.requiredLeaseNanos()));
    }

    /** 锁内开启本地短事务；提交或回滚完成后由公共模板释放资源锁。 */
    @Override
    protected WriteResult processWithinLock(ChangeSubjectTagStatusRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        return transaction.execute(status -> changeDictionary(request));
    }

    /** 锁定当前记录并检查版本，保存完整上下文；同值修改也递增版本。 */
    WriteResult changeDictionary(ChangeSubjectTagStatusRequest request) {
        RuleSubjectTag value = repository.lockByCode(request.getTagCode())
            .orElseThrow(() -> new DictionaryConflictException(Reason.NOT_FOUND));
        value.verifyIntegrity();
        value.verifyExpectedVersion(request.getExpectedVersion());
        var context = new SubjectTagChangeContext(value, new Content(value.getTagName(), value.getDescription(), null),
            request.getStatus(), request.getActorAccountId(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        service.saveSubjectTagChange(context);
        return new WriteResult(DictionaryType.SUBJECT_TAG, value.getTagCode(), value.getVersion(), value.getStatus(),
            value.getUpdatedBy(), value.getUpdateTime());
    }
}
