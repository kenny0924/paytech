package com.paytech.gateway.routing;

/** Operations 路由的 Query String 接受策略。 */
public enum OperationsQueryPolicy {
    REJECT_ALL;

    public boolean permits(String rawQuery) {
        return rawQuery == null;
    }
}
