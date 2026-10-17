package com.paytech.risk.decision.domainservice.repository;

import java.time.Instant;
import java.util.List;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;

/** RuleSetVersion 单表读取。 */
public interface RuleSetVersionRepository {
    /** 批量读取准确 ID 对应的本表事实。 */
    List<RuleSetVersion> findByIds(List<Long> ids);

    /** CAS 退役，并在成功后修改传入原对象。 */
    void retire(RuleSetVersion value, Instant time);
    /** CAS 冻结，并在成功后修改传入原对象。 */
    void freeze(RuleSetVersion value, long actorId, Instant time);
    /** 插入准确新版本，唯一冲突按版本并发处理。 */
    void insert(RuleSetVersion value);
}
