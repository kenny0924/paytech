package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.EmailAuthenticationChallenge;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;

/** EmailAuthenticationChallenge 单表持久化合同；更新成功后同步传入对象。 */
public interface EmailAuthenticationChallengeRepository {
    /** 读取该账号同用途的最近挑战，供重发冷却判断。 */
    EmailAuthenticationChallenge latestChallenge(long accountId, ClientChallengePurpose purpose);

    /** 按公开编号查找 CUSTOMER 邮件挑战。 */
    EmailAuthenticationChallenge challenge(String challengeNo);

    /** 在账号锁之后锁定原挑战并刷新查询缓存。 */
    EmailAuthenticationChallenge lockChallenge(long id);

    /** 保存新挑战，仅持久化 HMAC。 */
    void insertChallenge(EmailAuthenticationChallenge row);

    /** 按状态与版本关闭原活动挑战。 */
    void supersedeChallenge(EmailAuthenticationChallenge row, Instant now);

    /** 单次消费仍在有效期和尝试上限内的挑战。 */
    void consumeChallenge(EmailAuthenticationChallenge row, Instant now);

    /** 保存失败次数，并在到期或耗尽时关闭挑战。 */
    void failChallenge(EmailAuthenticationChallenge row, Instant now);

}
