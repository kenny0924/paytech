package com.paytech.platform.support.integration.domain.execution;

import java.util.Objects;
import java.util.function.Function;

/**
 * 卡值句柄（PayerMax 卡直付对接技术方案第 3.2 节）：包装一项卡原值，只在本次调用内存中存在。
 *
 * <p>
 * 与 {@link ProtectedHandle} 分开：卡值只能写入原子 API 声明的卡字段，由请求体序列化在发送字节中展开，不能作为脚本函数的受控句柄参数或 Header 值。 不提供 getter
 * （表达式的属性读取依赖 getter），toString 与遮蔽文本不含原值，不实现 equals。
 */
public final class CardValue {

    /** 非卡号字段在执行轨迹中的遮蔽文本。 */
    public static final String MASK = "[CARD]";

    private final String secret;
    private final String masked;

    private CardValue(String secret, String masked) {
        this.secret = Objects.requireNonNull(secret, "secret");
        this.masked = masked;
    }

    /** 卡号：执行轨迹保留前 6 后 4 位，其余为星号。 */
    public static CardValue pan(String pan) {
        Objects.requireNonNull(pan, "pan");
        int hidden = Math.max(0, pan.length() - 10);
        String masked = pan.length() <= 10 ? MASK : pan.substring(0, 6) + "*".repeat(hidden) + pan.substring(pan.length() - 4);
        return new CardValue(pan, masked);
    }

    /** 卡号以外的卡值：执行轨迹中为 [CARD]。 */
    public static CardValue of(String value) {
        return new CardValue(value, MASK);
    }

    /**
     * 请求体序列化使用原值；原值不得被返回或记录。
     *
     * @param function 以原值为输入的计算
     * @return 计算结果
     */
    public <T> T apply(Function<String, T> function) {
        return function.apply(secret);
    }

    /** 执行轨迹与沙箱验证证据中的遮蔽文本。 */
    public String masked() {
        return masked;
    }

    @Override
    public String toString() {
        return MASK;
    }
}
