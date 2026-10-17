package com.paytech.risk.decision.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domainservice.repository.RuleSetVersionRepository;
import lombok.AllArgsConstructor;

/** 规则版本单表读写；状态 CAS 成功后更新传入原对象。 */
@Repository
@AllArgsConstructor
public class RuleSetVersionRepositoryImpl implements RuleSetVersionRepository {
    private static final Pattern MYSQL_DUPLICATE_KEY = Pattern.compile(
        "(?i)for\\s+key\\s+(?:['`]?[A-Za-z0-9_]+['`]?\\.)?['`]?([A-Za-z0-9_]+)['`]?"
    );

    private final DecisionRuleSetMapper mapper;

    /** 批量查询保留原 SQL 的坐标与稳定顺序。 */
    @Override
    public List<RuleSetVersion> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : List.copyOf(mapper.selectRuleSetVersionsByIds(ids));
    }

    /** 只改变已锁定本表版本，成功后同步其内存状态。 */
    @Override
    public void retire(RuleSetVersion value, Instant time) {
        requireCas(mapper.retireRuleSetVersion(value.ruleSetVersionId(), RuleSetVersionStatus.DRAFT, time));
        value.markRetired(time);
    }

    /** 内容 Hash 和 DRAFT 状态共同保护冻结。 */
    @Override
    public void freeze(RuleSetVersion value, long actorId, Instant time) {
        requireCas(mapper.freezeRuleSetVersion(value.ruleSetVersionId(), RuleSetVersionStatus.DRAFT, value.contentHash(), actorId, time, time));
        value.markFrozen(actorId, time);
    }

    /** 锁后的条件更新行数异常视为持久化不变量损坏。 */
    private static void requireCas(int affected) {
        if (affected != 1) throw new ErrorException("版本状态更新受影响行数异常: actual=" + affected);
    }

    /** 仅映射已批准的版本唯一索引冲突，不吞其他主键或约束故障。 */
    @Override
    public void insert(RuleSetVersion value) {
        try {
            if (mapper.insertRuleSetVersion(value) != 1) throw new ErrorException("插入规则版本行数异常");
        } catch (DuplicateKeyException exception) {
            if (!isRuleSetVersioningConflictMessage(exception.getMostSpecificCause().getMessage()))
                throw exception;
            throw new ConcurrentRequestException("Rule Set Version 已被并发占用", exception);
        }
    }

    /** 只识别 MySQL 已登记的业务版本唯一索引。 */
    static boolean isRuleSetVersioningConflictMessage(String mysqlMessage) {
        if (mysqlMessage == null) {
            return false;
        }
        Matcher matcher = MYSQL_DUPLICATE_KEY.matcher(mysqlMessage);
        if (!matcher.find()) {
            return false;
        }
        return "uk_rule_set_ver".equals(matcher.group(1));
    }
}
