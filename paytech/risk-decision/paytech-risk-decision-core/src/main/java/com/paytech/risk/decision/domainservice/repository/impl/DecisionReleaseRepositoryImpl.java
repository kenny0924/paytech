package com.paytech.risk.decision.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionRelease;
import com.paytech.risk.decision.core.dal.mapper.DecisionReleaseMapper;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domainservice.repository.DecisionReleaseRepository;

import lombok.AllArgsConstructor;

/** 根更新保留 SQL 条件与影响行数保护，成功后同步传入对象。 */
@Repository
@AllArgsConstructor
public class DecisionReleaseRepositoryImpl implements DecisionReleaseRepository {
    private static final Pattern MYSQL_DUPLICATE_KEY = Pattern.compile(
        "(?i)for\\s+key\\s+(?:['`]?[A-Za-z0-9_]+['`]?\\.)?['`]?([A-Za-z0-9_]+)['`]?"
    );

    private final DecisionReleaseMapper releaseMapper;

    /** 查询准确发布身份。 */
    @Override
    public Optional<DecisionRelease> findRoot(long releaseId) {
        return Optional.ofNullable(releaseMapper.selectRelease(releaseId));
    }

    /** 数据库行锁串行化校验开始、恢复和完成。 */
    @Override
    public Optional<DecisionRelease> lockRoot(long releaseId) {
        return Optional.ofNullable(releaseMapper.lockRelease(releaseId));
    }

    /** 查询分页根，不组装其他表的事实。 */
    @Override
    public List<DecisionRelease> findPage(String pointCode, DecisionReleaseStatus status, int limit, int offset) {
        return List.copyOf(releaseMapper.selectReleases(pointCode, status, limit, offset));
    }

    /** 与分页使用相同筛选。 */
    @Override
    public long count(String pointCode, DecisionReleaseStatus status) {
        return releaseMapper.countReleases(pointCode, status);
    }

    /** 锁内分配，仍由唯一索引兜底。 */
    @Override
    public int nextReleaseNoWhileCheckoutPointLocked() {
        long invalid = releaseMapper.countNonCanonicalReleaseNumbers();
        if (invalid != 0) {
            throw corruption("存在非规范 Release No，拒绝分配新编号");
        }
        long maximum = releaseMapper.selectMaxReleaseNo();
        try {
            if (maximum < 0) {
                throw corruption("Release No 最大值不能为负数");
            }
            return Math.toIntExact(Math.addExact(maximum, 1L));
        } catch (ArithmeticException exception) {
            throw new ErrorException("Release No 已超出首期整数范围", exception);
        }
    }

    /** 插入发布根，唯一编号冲突按并发失败返回。 */
    @Override
    public void insert(DecisionRelease release) {
        try {
            requireAffected(releaseMapper.insertRelease(release), 1, "插入 Decision Release");
        } catch (DuplicateKeyException exception) {
            if (!isReleaseNumberConflict(exception)) { throw exception; }
            throw new ConcurrentRequestException("Decision Release 编号已被并发占用", exception);
        }
    }

    /** 先检查数据库 CAS，再同步原根。 */
    @Override
    public void startValidation(DecisionRelease release, Instant time) {
        requireCas(releaseMapper.moveReleaseToValidating(release.releaseId(), DecisionReleaseStatus.DRAFT,
            1, time), "Candidate 进入 VALIDATING");
        release.applyValidationStarted(time);
    }

    /** 先检查数据库 CAS，再同步原根。 */
    @Override
    public void rejectValidation(DecisionRelease release, Instant time) {
        requireCas(releaseMapper.moveReleaseToRejected(release.releaseId(), DecisionReleaseStatus.VALIDATING,
            2, time), "Candidate 进入 REJECTED");
        release.applyValidationRejected(time);
    }

    private static void requireAffected(int actual, int expected, String operation) {
        if (actual != expected) {
            throw corruption(operation + " 受影响行数异常: expected=" + expected + ", actual=" + actual);
        }
    }

    private static void requireCas(int actual, String operation) {
        if (actual != 1) {
            throw corruption(operation + "受影响行数异常: actual=" + actual);
        }
    }

    private static boolean isReleaseNumberConflict(DuplicateKeyException exception) {
        Throwable cause = exception.getMostSpecificCause();
        return cause != null && isReleaseNumberConflictMessage(cause.getMessage());
    }

    static boolean isReleaseNumberConflictMessage(String mysqlMessage) {
        if (mysqlMessage == null) {
            return false;
        }
        Matcher matcher = MYSQL_DUPLICATE_KEY.matcher(mysqlMessage);
        return matcher.find() && "uk_release_no".equals(matcher.group(1));
    }

    private static ErrorException corruption(String message) {
        return new ErrorException(message);
    }
}
