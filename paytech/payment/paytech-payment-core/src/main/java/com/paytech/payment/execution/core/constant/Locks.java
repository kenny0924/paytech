package com.paytech.payment.execution.core.constant;

/**
 * @author lzb
 */
public class Locks {

    public static String getIntentLock(Long intentId) {
        return "intent:" + intentId;
    }

    /** 同一来源只创建一个支付意图 */
    public static String getIntentSourceLock(String sourceType, Long sourceId) {
        return "intent-source:" + sourceType + ":" + sourceId;
    }
}
