package com.paytech.foundation.messaging.api;

/**
 * 领域事件 Handler 与消费登记之间的事务关系（design/08 §4 第 4 步）。
 *
 * @author Claude
 */
public enum ConsumeTransaction {

    /** Handler 加入拦截器开启的本地事务，业务写入与消费登记同事务提交或回滚。 */
    SHARED,

    /**
     * Handler 在事务外调用并自行管理本地事务；判定重放、旧版本与缺口的短事务在 Handler 之前，消费登记的短事务在 Handler
     * 成功之后。Handler 必须具备业务幂等，以吸收两次提交之间中断造成的重投。
     */
    HANDLER_MANAGED
}
