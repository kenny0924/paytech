package com.paytech.counterparty.api.client.v1.common;

/** counterparty Client API 契约统一声明的服务入站路径（与 acquiring 的 Client API 前缀相同，由 Gateway 按路由分发）。 */
public final class ClientApiPaths {

    /** Gateway 调用 Client API 的路径前缀，应用 Context Path 由服务配置追加。 */
    public static final String V1 = "/client-gateway-api/v1";

    private ClientApiPaths() {}
}
