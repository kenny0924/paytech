package com.paytech.risk.control.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.dal.mapper.ClientUnderwritingMapper;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingException;
import com.paytech.risk.control.domain.underwriting.UnderwritingException.Reason;
import com.paytech.risk.control.domain.underwriting.UnderwritingListFilter;
import com.paytech.risk.control.domain.underwriting.UnderwritingStatistics;
import com.paytech.risk.control.domain.underwriting.UnderwritingVerdict;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;

import lombok.AllArgsConstructor;

/** Client 承保版本持久化。 */
@Repository
@AllArgsConstructor
public class ClientUnderwritingRepositoryImpl implements ClientUnderwritingRepository {
    /** 进行中槽位唯一索引名，用于区分插入冲突的业务含义。 */
    static final String OPEN_SLOT_INDEX = "uk_client_underwriting_open";

    private final ClientUnderwritingMapper mapper;

    /** 插入版本行；唯一索引冲突按索引转换为业务冲突。 */
    @Override
    public void insert(ClientUnderwriting row) {
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            String message = String.valueOf(duplicate.getMostSpecificCause().getMessage());
            if (message.contains(OPEN_SLOT_INDEX)) {
                throw new UnderwritingException(Reason.OPEN_EXISTS, "该 Client 已有进行中的承保: " + row.getClientId());
            }
            throw new UnderwritingException(Reason.VERSION_CONFLICT, "该 Client 的承保已被并发修改: " + row.getClientId());
        }
    }

    /** 按 ID 读取版本行。 */
    @Override
    public Optional<ClientUnderwriting> findById(long underwritingId) {
        return Optional.ofNullable(mapper.selectById(underwritingId));
    }

    /** 读取进行中版本。 */
    @Override
    public Optional<ClientUnderwriting> findOpen(long clientId) {
        return Optional.ofNullable(mapper.selectOpen(clientId));
    }

    /** 读取有效版本。 */
    @Override
    public Optional<ClientUnderwriting> findEffective(long clientId) {
        return Optional.ofNullable(mapper.selectEffective(clientId));
    }

    /** 批量读取有效版本。 */
    @Override
    public List<ClientUnderwriting> findEffectiveByClientIds(List<Long> clientIds) {
        return clientIds.isEmpty() ? List.of() : mapper.selectEffectiveByClientIds(clientIds);
    }

    /** 读取最新版本。 */
    @Override
    public Optional<ClientUnderwriting> findLatest(long clientId) {
        return Optional.ofNullable(mapper.selectLatest(clientId));
    }

    /** 读取版本历史。 */
    @Override
    public List<ClientUnderwriting> findHistory(long clientId, int limit) {
        return mapper.selectHistory(clientId, limit);
    }

    /** 分页读取。 */
    @Override
    public List<ClientUnderwriting> findPage(UnderwritingListFilter filter, int limit, long offset) {
        return mapper.selectPage(filter, limit, offset);
    }

    /** 统计同一筛选条件下的版本数。 */
    @Override
    public long count(UnderwritingListFilter filter) {
        return mapper.count(filter);
    }

    /** 五个单表计数，管理统计允许观察相邻已提交状态。 */
    @Override
    public UnderwritingStatistics statistics(Instant monthStart, Instant monthEnd) {
        return new UnderwritingStatistics(mapper.countEffective(), mapper.countByStatus(UnderwritingStatus.DRAFT),
            mapper.countByStatus(UnderwritingStatus.SUBMITTED),
            mapper.countDecided(List.of(UnderwritingStatus.APPROVED, UnderwritingStatus.SUPERSEDED), monthStart, monthEnd),
            mapper.countDecided(List.of(UnderwritingStatus.DECLINED), monthStart, monthEnd));
    }

    /** 条件更新成功后同步原版本行。 */
    @Override
    public void updateDraft(ClientUnderwriting row, UnderwritingContent content, Instant now) {
        requireOne(mapper.updateDraft(row, valuesOf(content), now), row);
        content.applyTo(row);
        advance(row, now);
    }

    /** 条件更新成功后同步原版本行。 */
    @Override
    public void markSubmitted(ClientUnderwriting row, UnderwritingContent content, long submittedBy, Instant now) {
        requireOne(mapper.markSubmitted(row, valuesOf(content), submittedBy, now), row);
        content.applyTo(row);
        row.setStatus(UnderwritingStatus.SUBMITTED);
        row.setSubmittedBy(submittedBy);
        row.setSubmittedTime(now);
        advance(row, now);
    }

    /** 条件更新成功后同步原版本行。 */
    @Override
    public void decide(ClientUnderwriting row, UnderwritingContent content, UnderwritingVerdict verdict) {
        requireOne(mapper.decide(row, content == null ? null : valuesOf(content), verdict), row);
        if (content != null) {
            content.applyTo(row);
            row.setSubmittedBy(verdict.getSubmittedBy());
            row.setSubmittedTime(verdict.getDecidedTime());
        }
        row.setStatus(verdict.getStatus());
        row.setOpenSlot(null);
        row.setEffectiveSlot(verdict.approved() ? Boolean.TRUE : null);
        row.setDecisionSource(verdict.getDecisionSource());
        row.setDecidedBy(verdict.getDecidedBy());
        row.setDecidedTime(verdict.getDecidedTime());
        row.setDeclineReason(verdict.getDeclineReason());
        row.setReviewComment(verdict.getReviewComment());
        row.setSupersedesUnderwritingId(verdict.getSupersedesUnderwritingId());
        advance(row, verdict.getDecidedTime());
    }

    /** 条件更新成功后同步原版本行。 */
    @Override
    public void supersede(ClientUnderwriting row, Instant now) {
        requireOne(mapper.supersede(row, now), row);
        row.setStatus(UnderwritingStatus.SUPERSEDED);
        row.setEffectiveSlot(null);
        row.setSupersededTime(now);
        advance(row, now);
    }

    /** 承载 SQL 参数的内容列；不替代原版本行。 */
    static ClientUnderwriting valuesOf(UnderwritingContent content) {
        ClientUnderwriting values = new ClientUnderwriting();
        content.applyTo(values);
        return values;
    }

    /** 行数不符说明版本行已被并发修改。 */
    static void requireOne(int updated, ClientUnderwriting row) {
        if (updated != 1) {
            throw new UnderwritingException(Reason.VERSION_CONFLICT, "承保已被其他人修改: " + row.getUnderwritingId());
        }
    }

    /** 版本加一并记录更新时间。 */
    static void advance(ClientUnderwriting row, Instant now) {
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }
}
