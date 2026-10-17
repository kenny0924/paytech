package com.paytech.risk.decision.domainservice.repository;

import java.time.Instant;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;

/** 规则集管理根的单表写入能力。 */
public interface RuleSetRepository {
    /** 同首版内容一起创建根。 */
    void insertRuleSet(RuleSet value);
    /** CAS 推进指针及管理版本，成功后更新原对象。 */
    void advanceRuleSet(RuleSet previous, long currentVersionId, Instant time);
    /** CAS 保存不可变删除事实，成功后更新原对象。 */
    void deleteRuleSet(RuleSet previous, long actorId, String triggerId, Instant time);
}
