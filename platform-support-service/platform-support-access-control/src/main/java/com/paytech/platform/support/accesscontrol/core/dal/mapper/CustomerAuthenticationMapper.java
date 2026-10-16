package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Issue;
import org.apache.ibatis.annotations.Param;

/** CUSTOMER 认证的本库持久化边界。 */
public interface CustomerAuthenticationMapper {
    /** 读取同一 CUSTOMER 账号的密码凭据。 */
    CustomerPassword password(@Param("accountId") long accountId);
    /** 读取 CLIENT 会话。 */
    ClientSession session(@Param("sessionId") long sessionId);
    /** 在事务中锁定 CLIENT 会话并清除旧缓存。 */
    ClientSession lockSession(@Param("sessionId") long sessionId);
    /** 按有界公开查找段读取 CLIENT 刷新代。 */
    ClientRefresh refresh(@Param("lookup") String lookup);
    /** 锁定同一 CLIENT 刷新代并读取最新状态。 */
    ClientRefresh lockRefresh(@Param("lookup") String lookup);
    /** 读取唯一 CUSTOMER ACTIVE 签名密钥。 */
    ClientSigningKey signingKey();
    /** 递增密码失败次数并在十次失败后锁定十五分钟。 */
    int passwordFailure(@Param("accountId") long accountId, @Param("expectedVersion") int expectedVersion, @Param("now") Instant now);
    /** 成功登录按当前账号快照清除失败计数。 */
    int passwordSuccess(@Param("accountId") long accountId, @Param("expectedVersion") int expectedVersion, @Param("credentialVersion") int credentialVersion, @Param("epoch") int epoch, @Param("now") Instant now);
    /** 保存新 CLIENT 会话；上下文、租户与成员取自签发准备结果。 */
    int insertSession(@Param("issue") Issue issue);
    /** 保存唯一活动刷新代，只保存 HMAC。 */
    int insertRefresh(@Param("issue") Issue issue);
    /** 单次消费仍有效的当前刷新代。 */
    int consumeRefresh(@Param("id") long id, @Param("sessionId") long sessionId, @Param("generation") int generation, @Param("now") Instant now);
    /** 登记已消费刷新代的稳定后继。 */
    int linkRefresh(@Param("id") long id, @Param("nextId") long nextId);
    /** 按会话版本推进刷新代与空闲期限。 */
    int advanceSession(@Param("expectedVersion") int expectedVersion, @Param("issue") Issue issue);
    /** 将活动 CLIENT 会话转为不可恢复终态。 */
    int revokeSession(@Param("sessionId") long sessionId, @Param("status") OperationsSessionStatus status, @Param("reason") String reason, @Param("now") Instant now);
    /** 撤销指定 CLIENT 会话的全部未消费刷新代。 */
    int revokeRefresh(@Param("sessionId") long sessionId, @Param("now") Instant now);
}
