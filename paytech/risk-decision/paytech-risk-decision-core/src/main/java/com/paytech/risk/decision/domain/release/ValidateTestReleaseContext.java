package com.paytech.risk.decision.domain.release;

import java.util.List;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ValidatedCandidate;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;

import lombok.Getter;

/** 一次发布校验持有的实际根与不可变审计事实，不保存 HTTP 请求身份。 */
@Getter
public class ValidateTestReleaseContext {
    /** 数据库锁定的原发布根；保存成功后原地同步。 */
    private final DecisionRelease release;
    /** 原开始审计；新校验构造时一次准备，中断恢复时沿用。 */
    private final ReleaseAuditEvent started;
    /** 已完成或待原子保存的固定八项检查，尚未完成时为空。 */
    private final List<ReleaseAuditEvent> checks;
    /** 失败时与检查一同保存的拒绝审计；通过时为空。 */
    private final ReleaseAuditEvent rejected;

    /** 根据同一快照中已核验的实际记录一次构造。 */
    public ValidateTestReleaseContext(DecisionRelease release, ReleaseAuditEvent started,
        List<ReleaseAuditEvent> checks, ReleaseAuditEvent rejected) {
        this.release = release;
        this.started = started;
        this.checks = List.copyOf(checks);
        this.rejected = rejected;
    }

    /** 发布的原校验只有同一操作者和原 expectedVersion 可以继续或读取完成结果。 */
    public void verifyOriginalValidation(long actorAccountId, int expectedVersion) {
        if (started == null) { throw new ErrorException("发布校验缺少开始审计"); }
        if (expectedVersion != 1 || started.actorId() == null || started.actorId() != actorAccountId) {
            throw new ConcurrentRequestException("必须使用原校验操作者和原 expectedVersion 恢复发布校验");
        }
    }

    /** 由真实审计数量派生完成状态，部分检查不能作为成功返回。 */
    public boolean completed() {
        if (!checks.isEmpty() && checks.size() != 8) { throw new ErrorException("发布校验缺少完整八项结果"); }
        return checks.size() == 8;
    }

    /** 从当前根与八项事实派生结果，保存后无需重新加载闭包。 */
    public ValidatedCandidate result() {
        if (!completed()) { throw new ErrorException("发布校验尚未完成"); }
        List<StaticCheckResult> results = checks.stream().map(value -> new StaticCheckResult(
            StaticCheckCode.valueOf(value.checkCode()), value.checkStatus(), value.severity(),
            value.errorCode(), value.errorSummary())).toList();
        return new ValidatedCandidate(release.releaseId(), release.canonicalReleaseNo(), release.environmentCode(),
            release.status(), release.version(), release.contentHash(),
            checks.stream().allMatch(value -> value.checkStatus() == StaticCheckStatus.PASSED), results);
    }
}
