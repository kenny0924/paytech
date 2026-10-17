package com.paytech.platform.support.accesscontrol.domain.clientdeveloper;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredential;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 创建、轮换或撤销 API 凭证的本地保存上下文
 * <li>创建：只有 created；轮换：created 为新凭证、original 为被轮换的旧凭证；撤销：只有 original。</li>
 */
@Getter
@AllArgsConstructor
public final class ClientApiCredentialChange {
    /** 已重证的工作区坐标。 */
    private final ClientWorkspaceAccess access;

    /** 新凭证；撤销时为空。 */
    private final ClientApiCredential created;

    /** 新凭证的 PHC 校验值；撤销时为空。 */
    private final String verifierPhc;

    /** 新凭证 Scope 行主键，与 created.scopeCodes 一一对应。 */
    private final List<Long> scopeIds;

    /** 已锁定的旧凭证；创建时为空。 */
    private final ClientApiCredential original;

    /** 轮换宽限分钟数；0 表示立即撤销旧凭证。 */
    private final int graceMinutes;

    /** 本次写入的 UTC 微秒时间。 */
    private final Instant now;

    /** 旧凭证宽限截止：不晚于原到期时间。 */
    public Instant graceUntil() {
        Instant grace = now.plusSeconds(graceMinutes * 60L);
        return grace.isBefore(original.getExpiresTime()) ? grace : original.getExpiresTime();
    }

    /** 避免凭证坐标进入普通日志。 */
    @Override
    public String toString() {
        return "ClientApiCredentialChange[REDACTED]";
    }
}
