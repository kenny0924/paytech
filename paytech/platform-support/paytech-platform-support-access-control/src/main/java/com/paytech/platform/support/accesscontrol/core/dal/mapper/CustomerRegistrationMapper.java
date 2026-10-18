package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.EmailAuthenticationChallenge;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import org.apache.ibatis.annotations.Param;

/** CUSTOMER 注册及邮件挑战的本库持久化边界。 */
public interface CustomerRegistrationMapper {
    /** 按稳定邮箱 Token 查找 CUSTOMER 人工账号。 */
    CustomerAccount accountByEmail(@Param("token") String token);

    /** 读取 CUSTOMER 账号当前事实。 */
    CustomerAccount account(@Param("id") long id);

    /** 在本地写事务中锁定真实账号资源。 */
    CustomerAccount lockAccount(@Param("id") long id);

    /** 创建未验证账号，不建立 Tenant 或 Session。 */
    int insertAccount(@Param("id") long id, @Param("token") String token, @Param("masked") String masked, @Param("now") Instant now);

    /** 只写入不可逆 Argon2id verifier。 */
    int insertPassword(@Param("id") long id, @Param("accountId") long accountId, @Param("verifier") String verifier, @Param("now") Instant now);

    /** 读取该账号用途的最近签发时间用于重发冷却。 */
    EmailAuthenticationChallenge latestChallenge(@Param("accountId") long accountId, @Param("purpose") ClientChallengePurpose purpose);

    /** 按公开查找标识读取本期 CUSTOMER 挑战。 */
    EmailAuthenticationChallenge challenge(@Param("challengeNo") String challengeNo);

    /** 在账号锁之后锁定挑战行，保持固定锁顺序。 */
    EmailAuthenticationChallenge lockChallenge(@Param("id") long id);

    /** 以期望状态及版本关闭原活动链接。 */
    int supersedeChallenge(@Param("id") long id, @Param("version") int version, @Param("now") Instant now);

    /** 保存只有 HMAC 的新挑战。 */
    int insertChallenge(@Param("challenge") EmailAuthenticationChallenge challenge);

    /** CAS 单次消费仍有效的原挑战。 */
    int consumeChallenge(@Param("id") long id, @Param("version") int version, @Param("now") Instant now);

    /** 失败尝试持久化；到期或五次失败释放活动占位。 */
    int failChallenge(@Param("id") long id, @Param("version") int version, @Param("now") Instant now);

    /** 只有邮箱仍匹配的待验证账号可以激活。 */
    int activateAccount(@Param("id") long id, @Param("version") int version, @Param("token") String token, @Param("now") Instant now);

    /** 密码恢复推进凭据和安全纪元并清除登录失败状态。 */
    int resetAccount(@Param("id") long id, @Param("version") int version, @Param("now") Instant now);

    /** 在同一事务中替换原密码 verifier。 */
    int resetPassword(@Param("accountId") long accountId, @Param("credentialVersion") int credentialVersion, @Param("verifier") String verifier, @Param("now") Instant now);

    /** 密码恢复撤销该 CUSTOMER 账号所有 Channel 的会话。 */
    void revokeAccountSessions(@Param("accountId") long accountId, @Param("now") Instant now);

    /** 撤销该账号会话下的全部活动 Refresh。 */
    void revokeAccountRefresh(@Param("accountId") long accountId, @Param("now") Instant now);

    /** 新注册尝试替换未验证凭据；已验证账号不可覆盖。 */
    int replacePendingAccount(@Param("id") long id, @Param("version") int version, @Param("now") Instant now);
}
