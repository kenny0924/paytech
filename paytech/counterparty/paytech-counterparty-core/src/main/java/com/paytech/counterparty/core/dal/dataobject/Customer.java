package com.paytech.counterparty.core.dal.dataobject;

import java.time.Instant;

import com.paytech.counterparty.api.enums.CustomerStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_customer 的本库事实；consumerReference 不进入 toString。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    /** 本库 Customer 主键。 */
    private long customerId;

    /** 不透明的公开 Customer 编号。 */
    private String customerNo;

    /** 所属 Client。 */
    private long clientId;

    /** Client 传入的消费者引用原值。 */
    private String consumerReference;

    /** 有效时等于消费者引用，删除后为空。 */
    private String activeReference;

    /** Customer 状态。 */
    private CustomerStatus customerStatus;

    /** 删除时间。 */
    private Instant deletedTime;

    /** 乐观锁版本。 */
    private long version;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    @Override
    public String toString() {
        return "Customer[" + customerId + "]";
    }
}
