package com.paytech.channel.connector.api;

/**
 * MID 能力目录中由 channel-connector 解释的稳定代码（D06：能力代码只在 channel-connector 一处解析）。
 * <li>调用方只读取候选接口已解析的支持操作、金额上下限与 3DS 模式，不自行解析这些代码。</li>
 */
public final class ChannelCapabilityCodes {
    /** 动作能力前缀：{@code ACTION.<ChannelOperation>} 为 true 表示明确支持该操作。 */
    public static final String ACTION_PREFIX = "ACTION.";

    /** 本次交易维度匹配到的金额下限（交易币种主单位，含边界）。 */
    public static final String AMOUNT_MIN = "AMOUNT.MIN";

    /** 本次交易维度匹配到的金额上限（交易币种主单位，含边界）。 */
    public static final String AMOUNT_MAX = "AMOUNT.MAX";

    /** 3DS 能力（CODE）：REQUIRED、SUPPORTED、UNSUPPORTED；未声明表示未知，调用方按不支持处理。 */
    public static final String SECURITY_3DS_MODE = "SECURITY.3DS_MODE";

    /** SECURITY.3DS_MODE 取值：MID 的每笔卡交易都做 3DS。 */
    public static final String THREE_DS_REQUIRED = "REQUIRED";

    /** SECURITY.3DS_MODE 取值：MID 支持按请求做 3DS。 */
    public static final String THREE_DS_SUPPORTED = "SUPPORTED";

    /** SECURITY.3DS_MODE 取值：MID 不支持 3DS。 */
    public static final String THREE_DS_UNSUPPORTED = "UNSUPPORTED";

    private ChannelCapabilityCodes() {
    }
}
