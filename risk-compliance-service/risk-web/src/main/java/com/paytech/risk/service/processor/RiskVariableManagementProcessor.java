package com.paytech.risk.service.processor;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.risk.api.enums.RiskVariableErrorCode;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domain.variable.VariableReferences;
import com.paytech.risk.decision.domainservice.service.*;
import com.paytech.risk.service.transaction.RiskTransactionExecutor;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/** 变量根资源锁在数据库事务外；授权和外部调用均在进入本类事务前完成。 */
@Component
@AllArgsConstructor
public class RiskVariableManagementProcessor {
    private final Clock clock;
    private final RiskVariableService variables;
    private final RiskTransactionExecutor transactions;
    private final RiskVariableReferenceService references;

    /** 根、集合预览和分页总数必须来自同一快照，防止最新值投影混合两个提交。 */
    public Page list(Query query) {
        return mapped(() -> transactions.read(() -> variables.list(query, now())));
    }

    /** 详情的根、关联集合与当前值时间同一快照读取。 */
    public ViewResult detail(String code) {
        return mapped(() -> transactions.read(() -> new ViewResult(variables.detail(code), now())));
    }

    /** 创建只锁当前代码，依赖共享锁在本地事务内按编码排序获取。 */
    @ResourceLock(prefix = "decision:variable", key = "#code")
    public ViewResult create(String code, Create command, long actorId) {
        return mapped(() -> transactions.write(() -> new ViewResult(variables.create(command, actorId, now()), now())));
    }

    /** 先获取目标变量 Redis 锁，再通过根写锁和 CAS 更新当前允许字段。 */
    @ResourceLock(prefix = "decision:variable", key = "#code")
    public ViewResult update(String code, Update command, long actorId) {
        return mapped(() -> transactions.write(() -> new ViewResult(variables.update(code, command, actorId, now()), now())));
    }

    /** 删除事务内的首次数据库操作由领域服务执行目标根 FOR UPDATE。 */
    @ResourceLock(prefix = "decision:variable", key = "#code")
    public Entry delete(String code, long expectedVersion, long actorId) {
        return mapped(() -> transactions.write(() -> variables.delete(code, expectedVersion, actorId, now())));
    }

    /** 根版本与成员计数来自同一快照，后续页必须验证原根版本。 */
    public MemberPage members(String code, String pattern, int page, int size, Long version) {
        return mapped(() -> transactions.read(() -> variables.members(code, pattern, page, size, version, now())));
    }

    /** 同一个变量锁覆盖根 CAS 和整批成员替换，事务失败后不会留下半批数据。 */
    @ResourceLock(prefix = "decision:variable", key = "#code")
    public Entry replaceMembers(String code, long expectedVersion, List<Member> members, long actorId) {
        return mapped(() -> transactions.write(() -> variables.replaceMembers(code, expectedVersion, members, actorId, now())));
    }

    /** 一页的初始上界及根当前/历史判定保持同一数据库读取快照。 */
    public VariableReferences.Page references(String code, String scope, String cursor, int size) {
        return mapped(() -> transactions.read(() -> {
            variables.detail(code);
            return references.page(code, scope, cursor, size);
        }));
    }

    /** 当前值投影时刻不进入规则或流程的不可变内容。 */
    public record ViewResult(View value, Instant valuesAsOf) { }

    /** 与 MySQL 保持 UTC 微秒精度。 */
    Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    /** 只转换有明确客户端处理语义的领域失败，未知数据库/网络错误继续向上传播。 */
    public static <T> T mapped(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RiskVariableException failure) {
            throw new FailException(RiskVariableErrorCode.valueOf(failure.reason().name()), failure.getMessage(), failure);
        }
    }
}
