package com.paytech.platform.support.accesscontrol.domain.operations;

/**
 * 供其他模块使用的 OPERATIONS 人员信息（Owner 定义的领域结果，不暴露数据对象）。
 *
 * @param accountId 账号
 * @param displayName 显示名：登录名，没有时为脱敏邮箱（Q11）
 * @param active 账号、内部租户成员与租户均为 ACTIVE
 * @param emailToken 登录邮箱 Vault Token，只供通知投递在事务外 Reveal，禁止外显
 */
public record OperationsPersonnelInfo(long accountId, String displayName, boolean active, String emailToken) {

    /** 避免 Token 进入普通日志。 */
    @Override
    public String toString() {
        return "OperationsPersonnelInfo[accountId=" + accountId + ", active=" + active + "]";
    }
}
