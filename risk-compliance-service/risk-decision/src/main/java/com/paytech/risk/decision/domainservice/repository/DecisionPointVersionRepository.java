package com.paytech.risk.decision.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;

/** 旧点配置所属单表的只读查询，不开启事务。 */
public interface DecisionPointVersionRepository {
    /** 读取准确历史事实；绑定查询保留既有 201 条防超限边界。 */
    Optional<DecisionPointVersion> findById(long id);
    /** 批量读取准确 ID 对应的本表事实。 */
    List<DecisionPointVersion> findByIds(List<Long> ids);

    /** CAS 退役，并在成功后修改传入原对象。 */
    void retire(DecisionPointVersion value, Instant time);
    /** CAS 冻结，并在成功后修改传入原对象。 */
    void freeze(DecisionPointVersion value, Instant time);
}
