package com.paytech.acquiring.core.constant;

/** Commerce 资源锁 Key；修改同一资源的入口共用同一命名。 */
public final class Locks {

    private Locks() {}

    /**
     * 订单资源锁
     * <li>消费者支付提交与过期收尾共用，Key 只含不可变订单主键，不使用公开 Token。</li>
     *
     * @param orderId 订单主键
     * @return 锁 Key
     */
    public static String getOrderLock(long orderId) {
        return "order:" + orderId;
    }
}
