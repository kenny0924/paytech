package com.paytech.platform.support.auditsecurity.domainservice.service;

import java.util.function.Supplier;

import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact;

/** Operations 现场审计采集；只冻结白名单值，采集失败不影响业务。 */
public interface OperationsAuditService {
    /** 在当前本地事务开始后登记完成通知，供业务模块保留既有审计终态语义。 */
    void observeTransaction();

    /** 可信身份仅在认证完成后惰性求值。 */
    void identify(Supplier<Identity> identity);

    /** 动态资源仅在业务完成定位后惰性求值。 */
    void target(Supplier<Target> target);

    /** 同步求值并冻结业务事实；任何审计构造失败均不影响业务。 */
    void record(Supplier<? extends OperationsAuditFact> fact);

    /** 成功幂等重放不补造另一条变更审计。 */
    void suppressReplay();

}
