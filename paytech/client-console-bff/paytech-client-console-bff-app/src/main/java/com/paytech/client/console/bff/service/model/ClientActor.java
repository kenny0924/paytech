package com.paytech.client.console.bff.service.model;

import com.paytech.client.console.bff.domain.enums.ClientContextType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 入站边界解析的 CLIENT 可信身份；实际账号、会话与成员关系仍由 Platform 核验
 * <li>只来自 Gateway 重建的单值请求头，不接受浏览器提交。</li>
 */
@Getter
@AllArgsConstructor
public class ClientActor {

    /** 账号 ID，规范正整数 */
    private final String accountId;

    /** 会话 ID，规范正整数 */
    private final String sessionId;

    /** 会话上下文类型 */
    private final ClientContextType contextType;

    /**
     * 租户 ID
     * <li>请求头恰好一个值时为原值，否则为空串；只有与 Platform 实时上下文一致时才可信。</li>
     */
    private final String tenantId;

    /**
     * 租户成员 ID
     * <li>取值规则同 tenantId。</li>
     */
    private final String memberId;

    /** 是否处于租户上下文。 */
    public boolean isTenantContext() {
        return contextType == ClientContextType.TENANT;
    }
}
