package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.authentication.IssueClientEmailContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.ConsumeClientEmailContext;

/** Client 认证的本地业务保存合同。 */
public interface CustomerRegistrationService {
    /** 同一事务保存待验证账号、凭据和新挑战；替代原活动链接。 */
    void issue(IssueClientEmailContext context);
    /** 保存无效挑战的失败次数和必要终态。 */
    void denyProof(ConsumeClientEmailContext context);
    /** 单次消费验证挑战，并激活同一邮箱账号。 */
    void verifyEmail(ConsumeClientEmailContext context);
    /** 单次消费恢复挑战，原子替换密码并撤销账号全部会话。 */
    void resetPassword(ConsumeClientEmailContext context);
}
