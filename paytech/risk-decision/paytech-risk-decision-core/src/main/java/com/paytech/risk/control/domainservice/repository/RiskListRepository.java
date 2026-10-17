package com.paytech.risk.control.domainservice.repository;

import java.util.List;
import java.util.Optional;

import com.paytech.risk.control.core.dal.dataobject.RiskList;

/** 风险名单定义的单表持久化边界。 */
public interface RiskListRepository {
    /**
     * 插入名单
     *
     * @param row 已构造的名单
     *         <li>编码已被并发占用时抛出并发冲突。</li>
     */
    void insert(RiskList row);

    /** 按 ID 读取名单。 */
    Optional<RiskList> findById(long listId);

    /** 按编码读取名单。 */
    Optional<RiskList> findByCode(String listCode);

    /** 按编码稳定排序分页读取名单。 */
    List<RiskList> findPage(int limit, long offset);

    /** 统计名单数量。 */
    long count();
}
