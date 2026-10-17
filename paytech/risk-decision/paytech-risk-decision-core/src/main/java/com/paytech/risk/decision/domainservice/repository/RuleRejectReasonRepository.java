package com.paytech.risk.decision.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Content;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;

/** 拒绝原因表的持久化访问，数据对象仅在 Decision 模块内使用。 */
public interface RuleRejectReasonRepository {

    /** 按永久编码读取当前原因，不加锁、不排除停用记录。 */
    Optional<RuleRejectReason> findByCode(String reasonCode);

    /** 锁定规则首次选择的字典记录，保持与启停写入的数据库互斥。 */
    Optional<RuleRejectReason> lockForSelection(String code);

    /** 在调用方事务内按永久编码加写锁，供创建唯一性及后续编辑协调。 */
    Optional<RuleRejectReason> lockByCode(String reasonCode);

    /** 在调用方事务内按旧版本更新，SQL 成功后同步传入原对象。 */
    void update(RuleRejectReason value, Content content, DictionaryStatus status, long actorId, Instant time);

    /** 插入一条初始字典事实，唯一编码冲突返回业务重复。 */
    void insert(RuleRejectReason reason);

    /** 按原因编码或名称的字面包含条件及可选状态统计记录数。 */
    long count(String keyword, DictionaryStatus status);

    /** 按相同条件查询当前页，保持原因编码、主键升序。 */
    List<RuleRejectReason> findPage(String keyword, DictionaryStatus status, int limit, int offset);
}
