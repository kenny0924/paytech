package com.paytech.platform.support.accesscontrol.domain.operations;

/**
 * 选人结果（Owner 定义的领域结果）：只含可展示的标识，不含邮箱 Token 或原文。
 *
 * @param accountId 账号
 * @param loginName 登录名；人员账号通常为空
 * @param emailMasked 脱敏邮箱
 * @param displayName 显示名：登录名，没有时为脱敏邮箱（Q11）
 */
public record OperationsPersonnelCandidate(long accountId, String loginName, String emailMasked, String displayName) {
}
