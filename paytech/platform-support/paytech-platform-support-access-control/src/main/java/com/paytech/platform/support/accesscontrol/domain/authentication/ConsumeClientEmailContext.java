package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.EmailAuthenticationChallenge;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.domain.authentication.ConsumeClientEmailContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientRegistrationModels.LinkProof;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本次邮件挑战消费的完整本库事实；事务失败后整体丢弃。 */
@Getter
@AllArgsConstructor
public class ConsumeClientEmailContext {
    /** 已计算的秘密证明。 */
    private final LinkProof proof;
    /** 本次账号对象。 */
    private final CustomerAccount account;
    /** 本次密码对象；纯邮箱验证无需密码时可空。 */
    private final CustomerPassword password;
    /** 本次挑战对象。 */
    private final EmailAuthenticationChallenge challenge;
    /** 本次数据库操作时刻。 */
    private final Instant now;
}
