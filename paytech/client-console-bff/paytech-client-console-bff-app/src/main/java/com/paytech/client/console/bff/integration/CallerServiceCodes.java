package com.paytech.client.console.bff.integration;

/** 调用 Owner 时声明的调用方服务名（X-Caller-Service），由 Owner 用于归属与审计。 */
public final class CallerServiceCodes {
    /** 本服务 */
    public static final String CLIENT_CONSOLE_BFF = "client-console-bff";

    private CallerServiceCodes() { }
}
