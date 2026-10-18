package com.paytech.platform.support.accesscontrol.domainservice.service;

import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.authentication.*;

/** Client 认证的本地业务保存合同。 */
public interface CustomerAuthenticationService {
    /** 密码失败计数提交后，由用例入口返回认证拒绝。 */
    void passwordFailure(LoginClientContext context);
    /** 清除失败计数并保存新会话及首个刷新代。 */
    void login(LoginClientContext context);
    /** 原子消费旧代、保存后继并推进当前会话。 */
    void refresh(RefreshClientContext context);
    /** 正确旧令牌重用时撤销会话与剩余活动刷新代。 */
    void compromise(RefreshClientContext context);
    /** 账号安全纪元或会话失效时撤销其活动刷新代。 */
    void invalidate(RefreshClientContext context);
    /** 退出当前会话；已结束会话保持原终态。 */
    void logout(LogoutClientContext context);
    /** 供现有跨模块消费者读取当前有效的账号上下文；租户上下文会话视为无效。 */
    ClientAccountView context(long accountId, long sessionId);
    /** 读取当前有效的账号或租户上下文；租户上下文按当前成员与租户重证。 */
    ClientAccountView currentContext(long accountId, long sessionId);
    /** 当前会话有效时读取账号在非 PENDING CLIENT 租户中的工作区。 */
    List<ClientWorkspaceView> workspaces(long accountId, long sessionId);
    /** 撤销当前会话及其刷新代，保存切换后的新会话与首个刷新代。 */
    void switchContext(SwitchClientContext context);
    /** 撤销已失效的租户会话及其刷新代，保存降级后的账号上下文会话。 */
    void fallback(RefreshClientContext context);
}
