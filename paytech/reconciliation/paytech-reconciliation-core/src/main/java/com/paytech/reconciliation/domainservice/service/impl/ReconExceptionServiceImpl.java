package com.paytech.reconciliation.domainservice.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.OpenReconExceptionCommand;
import com.paytech.reconciliation.domain.ReconExceptionTypes;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;

import lombok.AllArgsConstructor;

/** 对账异常的开立与状态迁移；事务传播为 REQUIRED，在调用方事务内与其他写入一起提交。 */
@Service
@AllArgsConstructor
public class ReconExceptionServiceImpl implements ReconExceptionService {
    /** detail 列上限。 */
    static final int DETAIL_LIMIT = 1000;

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ReconExceptionRepository reconExceptionRepository;

    /** 开立或返回已有的未结异常。 */
    @Override
    public ReconException open(OpenReconExceptionCommand command) {
        return transactionTemplate.execute(status -> {
            ReconException existing = reconExceptionRepository.findActive(command.getSourceType(),
                command.getSourceId(), command.getExceptionType());
            if (existing != null) {
                return existing;
            }
            ReconException exception = build(command, now());
            reconExceptionRepository.insert(exception);
            return exception;
        });
    }

    /** 批量开立：按对象类型 + 异常类型分组读取已有未结异常，缺的按段批量插入。 */
    @Override
    public List<ReconException> openAll(List<OpenReconExceptionCommand> commands) {
        if (commands.isEmpty()) {
            return List.of();
        }
        return transactionTemplate.execute(status -> {
            Map<SourceKey, ReconException> opened = activeBySource(commands.stream()
                .map(command -> new SourceKey(command.getSourceType(), command.getSourceId(), command.getExceptionType()))
                .toList());
            Instant now = now();
            List<ReconException> created = new ArrayList<>();
            List<ReconException> result = new ArrayList<>();
            for (OpenReconExceptionCommand command : commands) {
                SourceKey key = new SourceKey(command.getSourceType(), command.getSourceId(), command.getExceptionType());
                ReconException exception = opened.computeIfAbsent(key, missing -> {
                    ReconException fresh = build(command, now);
                    created.add(fresh);
                    return fresh;
                });
                result.add(exception);
            }
            reconExceptionRepository.insertAll(created);
            return result;
        });
    }

    /** 批量自动结案：重新读取未结异常，只把 OPEN 的按版本转 AUTO_CLOSED。 */
    @Override
    public int autoCloseAll(List<ReconException> exceptions) {
        if (exceptions.isEmpty()) {
            return 0;
        }
        Integer closed = transactionTemplate.execute(status -> {
            List<ReconException> open = activeBySource(exceptions.stream()
                .map(SourceKey::of).toList()).values().stream()
                .filter(exception -> exception.getStatus() == ReconExceptionStatus.OPEN).toList();
            reconExceptionRepository.changeStatusAll(open, ReconExceptionStatus.AUTO_CLOSED, now());
            return open.size();
        });
        return closed == null ? 0 : closed;
    }

    /** 按对象类型 + 异常类型分组批量读取未结异常。 */
    Map<SourceKey, ReconException> activeBySource(List<SourceKey> keys) {
        Map<SourceKey, List<Long>> groups = keys.stream().distinct().collect(Collectors.groupingBy(
            key -> new SourceKey(key.sourceType(), null, key.exceptionType()), LinkedHashMap::new,
            Collectors.mapping(SourceKey::sourceId, Collectors.toList())));
        Map<SourceKey, ReconException> active = new LinkedHashMap<>();
        groups.forEach((group, sourceIds) -> reconExceptionRepository.listActiveBySources(group.sourceType(), sourceIds,
            group.exceptionType()).forEach(exception -> active.put(SourceKey.of(exception), exception)));
        return active;
    }

    /** OPEN 异常转为 AUTO_CLOSED；UNDER_REVIEW 不动。 */
    @Override
    public boolean autoClose(ReconExceptionSourceType sourceType, Long sourceId, ReconExceptionType exceptionType) {
        Boolean closed = transactionTemplate.execute(status -> {
            ReconException active = reconExceptionRepository.findActive(sourceType, sourceId, exceptionType);
            if (active == null || active.getStatus() != ReconExceptionStatus.OPEN) {
                return false;
            }
            reconExceptionRepository.changeStatus(active, ReconExceptionStatus.AUTO_CLOSED, now());
            return true;
        });
        return Boolean.TRUE.equals(closed);
    }

    /** OPEN → UNDER_REVIEW。 */
    @Override
    public void markUnderReview(ReconException exception) {
        transition(exception, ReconExceptionStatus.OPEN, ReconExceptionStatus.UNDER_REVIEW, null);
    }

    /** UNDER_REVIEW → OPEN。 */
    @Override
    public void reopen(ReconException exception) {
        transition(exception, ReconExceptionStatus.UNDER_REVIEW, ReconExceptionStatus.OPEN, null);
    }

    /** UNDER_REVIEW → RESOLVED。 */
    @Override
    public void resolve(ReconException exception) {
        transition(exception, ReconExceptionStatus.UNDER_REVIEW, ReconExceptionStatus.RESOLVED, now());
    }

    /** 按原状态迁移；内存状态已不符时直接报并发冲突，不发 SQL。 */
    void transition(ReconException exception, ReconExceptionStatus from, ReconExceptionStatus to, Instant closedTime) {
        if (exception.getStatus() != from) {
            throw new ConcurrentRequestException("对账异常状态已变更，请刷新后重试: " + exception.getId() + ", 当前状态 "
                + exception.getStatus().getCode());
        }
        transactionTemplate.executeWithoutResult(status -> reconExceptionRepository.changeStatus(exception, to,
            closedTime));
    }

    /** 由命令构造 OPEN 异常；层与阻塞由类型派生。 */
    ReconException build(OpenReconExceptionCommand command, Instant now) {
        ReconException exception = new ReconException();
        exception.setId(idGenerator.nextId());
        exception.setExceptionType(command.getExceptionType());
        exception.setReconLayer(ReconExceptionTypes.layerOf(command.getExceptionType()));
        exception.setBlocking(ReconExceptionTypes.isBlocking(command.getExceptionType()));
        exception.setSourceType(command.getSourceType());
        exception.setSourceId(command.getSourceId());
        exception.setStatementId(command.getStatementId());
        exception.setClearingAccountId(command.getClearingAccountId());
        exception.setCashAccountId(command.getCashAccountId());
        exception.setEntityType(command.getEntityType());
        exception.setEntityId(command.getEntityId());
        exception.setCurrency(command.getCurrency());
        exception.setDifferenceAmount(command.getDifferenceAmount());
        exception.setDetail(truncate(command.getDetail()));
        exception.setStatus(ReconExceptionStatus.OPEN);
        exception.setActiveSlot(1);
        exception.setOpenedBy(command.getOpenedBy());
        exception.setOpenedTime(now);
        exception.setVersion(1L);
        exception.setCreateTime(now);
        exception.setUpdateTime(now);
        return exception;
    }

    /** 说明超长时截断到列上限。 */
    static String truncate(String detail) {
        return detail == null || detail.length() <= DETAIL_LIMIT ? detail : detail.substring(0, DETAIL_LIMIT);
    }

    /** 写入时间：UTC 微秒。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 未结异常的业务键（对象类型、对象 ID、异常类型），对应 active_slot 唯一键。 */
    record SourceKey(ReconExceptionSourceType sourceType, Long sourceId, ReconExceptionType exceptionType) {
        /** 异常自身的业务键。 */
        static SourceKey of(ReconException exception) {
            return new SourceKey(exception.getSourceType(), exception.getSourceId(), exception.getExceptionType());
        }
    }
}
