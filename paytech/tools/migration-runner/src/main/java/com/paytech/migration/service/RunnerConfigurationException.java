package com.paytech.migration.service;

/**
 * 参数或运行环境错误（退出码 3）：缺环境变量、无法连接、连接到的库与预期不符等。
 *
 * <p>
 * 消息可安全输出，不含密码与完整 JDBC URL。
 *
 * @author Claude
 */
public class RunnerConfigurationException extends RuntimeException {

    /**
     * 构造环境错误。
     *
     * @param message 中文说明
     */
    public RunnerConfigurationException(String message) {
        super(message);
    }
}
