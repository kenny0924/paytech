package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Entry;
import com.paytech.risk.decision.domainservice.convert.dictionary.RejectionReasonConverter;
import com.paytech.risk.decision.domainservice.repository.RuleRejectReasonRepository;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException.Reason;
import com.paytech.risk.decision.service.processor.request.RejectionReasonDetailRequest;

import lombok.AllArgsConstructor;

/** 在 Decision 模块内查询拒绝原因详情，校验当前事实后返回领域结果。 */
@Component
@AllArgsConstructor
public class RejectionReasonDetailProcessor extends AbstractProcessor<RejectionReasonDetailRequest, Entry> {

    private final RuleRejectReasonRepository repository;

    /**
     * 读取当前原因，未登记返回领域未找到，停用仍可查询。
     * <li>单次无锁查询即可取得完整记录，不创建显式事务。</li>
     */
    @Override
    protected Entry processRequest(RejectionReasonDetailRequest request) {
        var reason = repository.findByCode(request.getReasonCode())
            .orElseThrow(() -> new DictionaryConflictException(Reason.NOT_FOUND));
        reason.verifyIntegrity();
        return RejectionReasonConverter.toEntry(reason);
    }
}
