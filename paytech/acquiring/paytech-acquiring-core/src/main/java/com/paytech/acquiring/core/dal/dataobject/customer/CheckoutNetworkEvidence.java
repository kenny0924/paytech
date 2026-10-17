package com.paytech.acquiring.core.dal.dataobject.customer;

import java.time.Instant;

import com.paytech.acquiring.domain.enums.CheckoutEvidenceScene;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Checkout 网络证据表对象，仅用于本库受限保存与归属检查。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutNetworkEvidence {
    /** 网络证据主键。 */
    private long networkEvidenceId;

    /** 证据所属订单。 */
    private long orderId;

    /** 证据所属 Checkout 会话。 */
    private long checkoutSessionId;

    /** 产生证据的业务场景。 */
    private CheckoutEvidenceScene evidenceScene;

    /** 公共请求标识。 */
    private String requestId;

    /** 可信连接来源地址。 */
    private String clientIpAddress;

    /** 原始 User-Agent，禁止普通日志输出。 */
    private String userAgent;

    /** 批准的受信代理链，未接入时为空。 */
    private String trustedProxyChain;

    /** 证据发生时刻。 */
    private Instant occurredAt;

    /** 记录创建时刻。 */
    private Instant createTime;
}

