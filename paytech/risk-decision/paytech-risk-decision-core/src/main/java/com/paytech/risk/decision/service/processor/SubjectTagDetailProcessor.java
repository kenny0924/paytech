package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Entry;
import com.paytech.risk.decision.domainservice.convert.dictionary.SubjectTagConverter;
import com.paytech.risk.decision.domainservice.repository.RuleSubjectTagRepository;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException.Reason;
import com.paytech.risk.decision.service.processor.request.SubjectTagDetailRequest;

import lombok.AllArgsConstructor;

/** 在 Decision 模块内查询拒绝标签详情，校验当前事实后返回领域结果。 */
@Component
@AllArgsConstructor
public class SubjectTagDetailProcessor extends AbstractProcessor<SubjectTagDetailRequest, Entry> {

    private final RuleSubjectTagRepository repository;

    /**
     * 读取当前标签，未登记返回领域未找到，停用仍可查询。
     * <li>单次无锁查询即可取得完整记录，不创建显式事务。</li>
     */
    @Override
    protected Entry processRequest(SubjectTagDetailRequest request) {
        var tag = repository.findByCode(request.getTagCode())
            .orElseThrow(() -> new DictionaryConflictException(Reason.NOT_FOUND));
        tag.verifyIntegrity();
        return SubjectTagConverter.toEntry(tag);
    }
}
