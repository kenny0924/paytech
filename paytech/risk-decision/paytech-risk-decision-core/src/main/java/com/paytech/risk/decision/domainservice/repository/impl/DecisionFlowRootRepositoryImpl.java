package com.paytech.risk.decision.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.mapper.DecisionFlowMapper;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRootRepository;

import lombok.AllArgsConstructor;

/** 仅操作所属流程表，保留数据库唯一键、条件更新与影响行数保护。 */
@Repository
@AllArgsConstructor
public class DecisionFlowRootRepositoryImpl implements DecisionFlowRootRepository {
    private final DecisionFlowMapper mapper;

    /** 按原主键读取本表事实。 */
    @Override
    public Optional<DecisionFlowRow> findById(long id) { return Optional.ofNullable(mapper.findRoot(id)); }

    /** 在调用方事务中按主键获取排他行锁。 */
    @Override
    public Optional<DecisionFlowRow> lockById(long id) { return Optional.ofNullable(mapper.lockRoot(id)); }

    /** 按不可变决策点和编码查询永久流程身份。 */
    @Override
    public Optional<DecisionFlowRow> findByCode(long pointId, String code) {
        return Optional.ofNullable(mapper.findByCode(pointId, code));
    }

    /** 根的旧版本及 CONFIGURABLE 条件由 SQL 同时保护。 */
    @Override
    public void advance(DecisionFlowRow root, DecisionFlowVersionRow version) {
        if (mapper.advance(root.flowId(), root.version(), version.flowVersionId(), version.createTime()) != 1) {
            throw new ConcurrentRequestException("流程配置已变化，请刷新后重试");
        }
        root.advance(version);
    }

    /** 仅首次条件删除成功后同步原对象，失败时对象保持原状。 */
    @Override
    public void delete(DecisionFlowRow root, long actorId, Instant time) {
        if (mapper.delete(root.flowId(), root.version(), actorId, time) != 1) {
            throw new ConcurrentRequestException("流程配置已变化，请刷新后重试");
        }
        root.delete(actorId, time);
    }

    /** 唯一键仍是资源锁租约失效后的最终防线，冲突回滚整笔保存。 */
    @Override
    public void insert(DecisionFlowRow value) {
        try {
            if (mapper.insertDecisionFlow(value) != 1) {
                throw new ErrorException("流程明细写入行数不一致");
            }
        } catch (DuplicateKeyException failure) {
            throw new ConcurrentRequestException("流程配置被并发更新，请刷新后重试", failure);
        }
    }

    /** 复用既有当前名称过滤 SQL，不改变排序或数据库分页。 */
    @Override
    public List<DecisionFlowRow> list(long pointId, String flowCode, String keyword, long offset, int limit) {
        return List.copyOf(mapper.listRoots(pointId, flowCode, pattern(keyword), offset, limit));
    }

    /** 总数与当前页由调用方放在同一快照。 */
    @Override
    public long count(long pointId, String flowCode, String keyword) {
        return mapper.countRoots(pointId, flowCode, pattern(keyword));
    }

    /** 名称包含匹配中的通配符按字面字符处理。 */
    private static String pattern(String keyword) {
        return keyword == null ? null : "%" + keyword.replace("!", "!!").replace("%", "!%")
            .replace("_", "!_") + "%";
    }
}
