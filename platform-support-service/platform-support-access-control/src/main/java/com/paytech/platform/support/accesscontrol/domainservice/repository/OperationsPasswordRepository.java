package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;

/** Operations 密码凭据事实访问。 */
public interface OperationsPasswordRepository {
    /** 读取当前 Argon2id 凭据。 */
    OperationsPassword password(long accountId);

    /** 按原凭据版本替换密码。 */
    void changePassword(OperationsPassword password, String verifier, Instant now);

    /** 保存强制首次改密的初始凭据。 */
    void insert(OperationsPassword password, Instant now);

    /** 使当前凭据永久失效。 */
    void revoke(OperationsPassword password, Instant now);

}
