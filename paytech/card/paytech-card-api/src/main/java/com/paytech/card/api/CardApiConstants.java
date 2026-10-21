package com.paytech.card.api;

/**
 * card-service 合同常量：服务发现名、调用方 Header 与各入口允许的调用方（D26，design/05 §4）
 * <li>internal-auth.mode=OFF 时 card 以 {@link #CALLER_HEADER} 识别调用方（过渡信任）；携带已验证内部 Token 时以 Token 调用方为准，两者不一致即拒绝。</li>
 */
public final class CardApiConstants {
    /** card 的服务发现名与内部 Token 的 aud；无 context path。 */
    public static final String SERVICE_ID = "card-service";

    /** 调用方服务名 Header。 */
    public static final String CALLER_HEADER = "X-Caller-Service";

    /** 工具创建与读卡票据签发的唯一调用方。 */
    public static final String PAYMENT_SERVICE = "payment-service";

    /** 凭票据读卡的调用方之一：外部 API 执行迁往 channel-connector 前的原调用方，第一段过渡期保留以便回退，第二段收回。 */
    public static final String PLATFORM_SUPPORT_SERVICE = "platform-support-service";

    /** 凭票据读卡的调用方之一：channel-connector 进程内执行标准 API 时读卡（2026-10-20 统一对外渠道出口第一段起）。 */
    public static final String CHANNEL_CONNECTOR_SERVICE = "channel-connector-service";

    /** 已存卡列出的调用方之一（GET checkout / SubmitPayment）。 */
    public static final String ACQUIRING_SERVICE = "acquiring-service";

    /** 已存卡列出、删除与失效的调用方之一（Client API Customer）。 */
    public static final String COUNTERPARTY_SERVICE = "counterparty-service";

    private CardApiConstants() {
    }
}
