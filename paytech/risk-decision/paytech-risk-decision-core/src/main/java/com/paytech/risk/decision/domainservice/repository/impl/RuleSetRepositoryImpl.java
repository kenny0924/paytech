package com.paytech.risk.decision.domainservice.repository.impl;

import java.time.Instant;
import org.springframework.stereotype.Repository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domainservice.repository.RuleSetRepository;
import lombok.AllArgsConstructor;

/** 管理根单表写入；数据库成功后更新上下文原对象。 */
@Repository
@AllArgsConstructor
public class RuleSetRepositoryImpl implements RuleSetRepository {
    private final DecisionRuleSetMapper mapper;

    /** 与首版内容一起插入整体根，并确认准确写入一行。 */
    @Override
    public void insertRuleSet(RuleSet value) {
        if (mapper.insertRuleSet(value) != 1) throw new ErrorException("创建规则集管理根行数异常");
    }

    /** CAS 成功后直接推进原管理根的指针、版本及时间。 */
    @Override
    public void advanceRuleSet(RuleSet previous, long currentVersionId, Instant time) {
        requireCas(mapper.advanceRuleSet(previous, currentVersionId, time), "推进规则集管理根");
        previous.setCurrentRuleSetVersionId(currentVersionId);
        previous.setVersion(previous.getVersion() + 1);
        previous.setUpdateTime(time);
    }

    /** CAS 成功后在原根记录不可变删除事实，不修改历史版本。 */
    @Override
    public void deleteRuleSet(RuleSet previous, long actorId, String triggerId, Instant time) {
        requireCas(mapper.deleteRuleSet(previous, actorId, triggerId, time), "整体删除规则集");
        previous.setStatus(RuleSetManagementStatus.DELETED);
        previous.setVersion(previous.getVersion() + 1);
        previous.setDeletedBy(actorId);
        previous.setDeletedTime(time);
        previous.setDeleteReasonCode("OPERATOR_REQUEST");
        previous.setDeleteTriggerId(triggerId);
        previous.setUpdateTime(time);
    }

    /** 持锁后的条件更新必须恰好影响一行。 */
    private static void requireCas(int affected, String label) {
        if (affected != 1) throw new ErrorException(label + "受影响行数异常: actual=" + affected);
    }
}
