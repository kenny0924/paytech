package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerPassword;

/** CustomerPassword 单表持久化合同；更新成功后同步传入对象。 */
public interface CustomerPasswordRepository {
    /** 读取同一 CUSTOMER 账号的密码凭据。 */
    CustomerPassword password(long accountId);

    /** 保存初始 Argon2id verifier，不保存密码明文。 */
    void insertPassword(CustomerPassword row, Instant now);

    /** 按当前凭据版本替换密码 verifier。 */
    void resetPassword(CustomerPassword row, String verifier, Instant now);

}
