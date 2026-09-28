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
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.WriteResult;
import com.paytech.risk.decision.domain.enums.DictionaryType;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.RuleSubjectTagRepository;
import com.paytech.risk.decision.domainservice.service.DecisionDictionaryService;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException.Reason;
import com.paytech.risk.decision.service.processor.request.CreateSubjectTagRequest;

import lombok.AllArgsConstructor;

/** 按永久标签编码协调创建，单次事务保存初始事实及构造本次结果。 */
@Component
@AllArgsConstructor
public class CreateSubjectTagProcessor extends AbstractLockProcessor<CreateSubjectTagRequest, WriteResult> {

    private final Clock clock;
    private final RuleSubjectTagRepository repository;
    private final DecisionDictionaryService service;
    private final PlatformTransactionManager transactionManager;
    private final DecisionIdentifierGenerator identifiers;
    private final RedisResourceLockProperties lockProperties;

    /**
     * 与现有字典编辑、启停共用完整逻辑 Key、普通 RLock 和有限等待/租约。
     * <li>迁移使用完整 Key 工厂，避免单锁工厂添加 lock: 后改变原互斥域。</li>
     */
    @Override
    protected LockCommand lockCommand(CreateSubjectTagRequest request) {
        return LockCommand.tryLocks(List.of("decision:dictionary:SUBJECT_TAG:" + request.getTagCode()),
            Duration.ofNanos(lockProperties.requiredWaitNanos()),
            Duration.ofNanos(lockProperties.requiredLeaseNanos()));
    }

    /** 锁内开启本地短事务；提交或回滚完成后由公共模板释放资源锁。 */
    @Override
    protected WriteResult processWithinLock(CreateSubjectTagRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        return transaction.execute(status -> createReason(request));
    }

    /** 已登记或已停用均拒绝复用编码；保存原对象后直接构造本次结果，不重新查询。 */
    WriteResult createReason(CreateSubjectTagRequest request) {
        if (repository.lockByCode(request.getTagCode()).isPresent()) {
            throw new DictionaryConflictException(Reason.CODE_EXISTS);
        }
        RuleSubjectTag tag = RuleSubjectTag.create(identifiers.nextIdentifier(), request.getTagCode(),
            request.getName(), request.getDescription(), request.getActorAccountId(),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        service.createSubjectTag(tag);
        return new WriteResult(DictionaryType.SUBJECT_TAG, tag.getTagCode(), tag.getVersion(),
            tag.getStatus(), tag.getUpdatedBy(), tag.getUpdateTime());
    }
}

