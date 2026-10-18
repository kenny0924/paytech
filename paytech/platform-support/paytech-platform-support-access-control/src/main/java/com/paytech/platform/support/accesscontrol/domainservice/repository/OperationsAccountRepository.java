package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;

/** Operations 账号事实访问。 */
public interface OperationsAccountRepository {
    /** 读取指定 OPERATIONS 账号。 */
    OperationsAccount account(long id);

    /** 按批准的邮箱 Token 或 Bootstrap 标识读取账号。 */
    OperationsAccount loginAccount(String token, boolean bootstrap);

    /** 首次管理员交接的最终数据库仲裁。 */
    OperationsAccount lockBootstrapAccount(long id);

    /** 统计已有 Operations 人员账号。 */
    long humanAccountCount();

    /** 保存密码失败次数及临时锁定。 */
    void passwordFailure(OperationsAccount account, Instant now);

    /** 按当前版本接受密码并清除失败次数。 */
    void passwordSuccess(OperationsAccount account, Instant now);

    /** 提升密码版本及账号撤销纪元。 */
    void changePassword(OperationsAccount account, Instant now);

    /** 插入新人员账号；唯一邮箱由数据库保护。 */
    void insert(OperationsAccount account, Instant now);

    /** 永久关闭 Bootstrap，不允许后续恢复。 */
    void disableBootstrap(OperationsAccount account, Instant now);

    /** CAS 停用人员，并推进安全撤销纪元。 */
    void disable(OperationsAccount account, OperationsMember member, Instant now);

    /** CAS 恢复账号，保持原密码与成员状态。 */
    void enable(OperationsAccount account, OperationsMember member, Instant now);

    /** CAS 永久关闭人员账号。 */
    void close(OperationsAccount account, Instant now);
}
