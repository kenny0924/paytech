package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;

/** CustomerAccount 单表持久化合同；更新成功后同步传入对象。 */
public interface CustomerAccountRepository {
    /** 按稳定邮箱 Token 查找 CUSTOMER 人工账号。 */
    CustomerAccount accountByEmail(String token);

    /** 读取 CUSTOMER 账号当前事实。 */
    CustomerAccount account(long id);

    /** 在本地事务中锁定账号，保护认证与恢复的共同不变量。 */
    CustomerAccount lockAccount(long id);

    /** 创建待验证账号，不建立 Tenant 或 Session。 */
    void insertAccount(CustomerAccount row, Instant now);

    /** 按原版本和邮箱激活待验证账号。 */
    void activateAccount(CustomerAccount row, Instant now);

    /** 推进账号凭据版本和安全纪元，并清除密码失败状态。 */
    void resetAccount(CustomerAccount row, Instant now);

    /** 推进未验证账号的凭据版本，保护重复注册的密码替换。 */
    void replacePendingAccount(CustomerAccount row, Instant now);

    /** 递增失败次数，达到十次时锁定十五分钟。 */
    void passwordFailure(CustomerAccount row, Instant now);

    /** 按当前账号快照清除失败计数。 */
    void passwordSuccess(CustomerAccount row, Instant now);

}
