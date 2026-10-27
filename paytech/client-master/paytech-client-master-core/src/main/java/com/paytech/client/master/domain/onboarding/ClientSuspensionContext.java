package com.paytech.client.master.domain.onboarding;

import java.time.Instant;

import com.paytech.client.master.core.dal.dataobject.ClientProjectionHistory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 资格撤销导致的 Client 挂起：ACTIVE → SUSPENDED 与下一版生命周期历史。 */
@Getter
@AllArgsConstructor
public class ClientSuspensionContext {
    /** 被挂起的 Client。 */
    private final long clientId;
    /** 追加的 SUSPENDED 生命周期历史。 */
    private final ClientProjectionHistory history;
    /** history 的不可变快照 JSON。 */
    private final String snapshot;
    /** 服务端时间。 */
    private final Instant now;
}
