package com.paytech.platform.support.integration.domain.execution;

import java.util.Objects;
import java.util.function.Function;

/**
 * 受控句柄（账号材料交接第 5 节）：包装一项凭据明文，只在本次调用内存中存在。
 *
 * <p>不提供 getter 或公开字段（表达式的属性读取依赖 getter），toString 不输出明文，不实现 equals；
 * 只有注册函数通过 {@link #apply(Function)} 使用明文，表达式与脚本只能传递句柄本身；
 * 原子 API credentialFields 声明的请求体字段由请求体序列化在发送字节中展开（Toss Pay 接入技术方案 T1）。
 */
public final class ProtectedHandle {

    /** 原子 API 凭据字段在执行轨迹与沙箱验证证据中的遮蔽文本。 */
    public static final String MASK = "[REDACTED]";

    private final String secret;

    public ProtectedHandle(String secret) {
        this.secret = Objects.requireNonNull(secret, "secret");
    }

    /**
     * 注册函数使用明文计算结果；明文不得被返回或记录。
     *
     * @param function 以明文为输入的计算
     * @return 计算结果
     */
    public <T> T apply(Function<String, T> function) {
        return function.apply(secret);
    }

    @Override
    public String toString() {
        return "ProtectedHandle[redacted]";
    }
}
