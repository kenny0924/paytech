package com.paytech.risk.decision.domainservice.repository.impl;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.core.dal.mapper.DecisionDictionaryMapper;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Content;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domainservice.repository.RuleRejectReasonRepository;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException.Reason;

import lombok.AllArgsConstructor;

/** 将字面关键词适配为已有 SQL 的 LIKE 参数，不开启事务。 */
@Repository
@AllArgsConstructor
public class RuleRejectReasonRepositoryImpl implements RuleRejectReasonRepository {

    private final DecisionDictionaryMapper mapper;

    /** 保留旧版本 CAS，写入失败不改变内存对象，成功后原位同步全部变更。 */
    @Override
    public void update(RuleRejectReason value, Content content, DictionaryStatus status, long actorId, Instant time) {
        if (mapper.updateReason(value.getReasonCode(), value.getVersion(), content.name(), content.description(),
            content.defaultMessage(), status, actorId, time) != 1) {
            throw new DictionaryConflictException(Reason.CHANGED);
        }
        value.applyChange(content.name(), content.description(), content.defaultMessage(), status, actorId, time);
    }

    /** 复用原因详情 SQL，以普通读取保留未登记和停用两种结果。 */
    @Override
    public Optional<RuleRejectReason> findByCode(String reasonCode) {
        return Optional.ofNullable(mapper.findReason(reasonCode, null));
    }

    /** 共享锁保持到调用方规则保存事务结束，停用和更新不能穿过资格检查。 */
    @Override
    public Optional<RuleRejectReason> lockForSelection(String code) {
        return Optional.ofNullable(mapper.findReason(code, "SHARE"));
    }

    /** 沿用字典当前读 SQL，在调用方事务中保持原行锁保护。 */
    @Override
    public Optional<RuleRejectReason> lockByCode(String reasonCode) {
        return Optional.ofNullable(mapper.findReason(reasonCode, "WRITE"));
    }

    /** 确认一条记录写入；仅编码唯一冲突返回业务重复，其他主键冲突保留为系统错误。 */
    @Override
    public void insert(RuleRejectReason reason) {
        try {
            if (mapper.insertReason(reason) != 1) {
                throw new ErrorException("字典创建未写入一行");
            }
        } catch (DuplicateKeyException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && sql.getErrorCode() == 1062
                    && sql.getMessage() != null && sql.getMessage().contains("uk_rule_reject_reason_code")) {
                    throw new DictionaryConflictException(Reason.CODE_EXISTS, failure);
                }
            }
            throw new ErrorException("字典创建标识冲突", failure);
        }
    }

    /** 复用原因表计数 SQL，状态和关键字未提供时不增加筛选。 */
    @Override
    public long count(String keyword, DictionaryStatus status) {
        return mapper.countReasons(literalPattern(keyword), status);
    }

    /** 复用原因表分页 SQL，保持原字段、筛选及排序。 */
    @Override
    public List<RuleRejectReason> findPage(String keyword, DictionaryStatus status, int limit, int offset) {
        return mapper.listReasons(literalPattern(keyword), status, limit, offset);
    }

    /** 对应 SQL 的 ESCAPE '!'；转义自身、百分号及下划线，反斜杠按普通字符匹配。 */
    private static String literalPattern(String keyword) {
        return keyword == null ? null : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
