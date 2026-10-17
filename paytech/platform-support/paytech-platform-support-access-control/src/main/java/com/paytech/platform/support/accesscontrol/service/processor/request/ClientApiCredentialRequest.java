package com.paytech.platform.support.accesscontrol.service.processor.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作区自助 API 凭证内部请求
 * <li>按操作使用其中的字段：查询用 status / credentialId；创建用 name、scopeCodes、validityDays；
 * 轮换用 credentialId、expectedVersion、graceMinutes；撤销用 credentialId、expectedVersion。</li>
 */
@Getter
@AllArgsConstructor
public final class ClientApiCredentialRequest {
    /** 可信账号编号。 */
    private final long accountId;
    /** 可信会话编号。 */
    private final long sessionId;
    /** 目标凭证；列表、创建时为空。 */
    private final Long credentialId;
    /** 列表状态筛选；可空。 */
    private final String status;
    /** 显示名称。 */
    private final String name;
    /** Scope 编码。 */
    private final List<String> scopeCodes;
    /** 有效期天数。 */
    private final int validityDays;
    /** 调用方看到的版本。 */
    private final int expectedVersion;
    /** 轮换宽限分钟数。 */
    private final int graceMinutes;

    /** 不输出会话坐标。 */
    @Override public String toString() { return "ClientApiCredentialRequest[REDACTED]"; }
}
