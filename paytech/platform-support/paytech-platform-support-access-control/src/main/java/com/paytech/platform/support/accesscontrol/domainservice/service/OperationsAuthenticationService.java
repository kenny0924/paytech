package com.paytech.platform.support.accesscontrol.domainservice.service;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.domain.authentication.ChangeOperationsPasswordContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.FirstOperationsAdministratorContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.LoginOperationsContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.LogoutOperationsContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.RefreshOperationsContext;

/** Operations 认证的本地原子保存，不查询或调用外部依赖。 */
public interface OperationsAuthenticationService {
    /** 保存失败计数；调用者提交后才返回认证失败。 */
    void passwordFailure(OperationsAccount account, Instant now);

    /** 原子接受密码、保存新会话及首个刷新代。 */
    void login(LoginOperationsContext context);

    /** 单次消费旧刷新代，保存后继并推进当前会话。 */
    void refresh(RefreshOperationsContext context);

    /** 正确旧代重用时撤销整个会话 Family。 */
    void compromise(RefreshOperationsContext context);

    /** 撤销已失去账号或成员资格的会话。 */
    void invalidate(RefreshOperationsContext context);

    /** 退出仅撤销当前会话，不恢复原终态。 */
    void logout(LogoutOperationsContext context);

    /** 原子更换密码、推进账号安全纪元并撤销全部会话。 */
    void changePassword(ChangeOperationsPasswordContext context);

    /** 原子建立首次管理员并永久关闭 Bootstrap。 */
    void createFirstAdministrator(FirstOperationsAdministratorContext context);

    /** 保留人员生命周期入口的本地账号会话撤销能力。 */
    void revokeAccountSessions(long accountId, String reason);

}
