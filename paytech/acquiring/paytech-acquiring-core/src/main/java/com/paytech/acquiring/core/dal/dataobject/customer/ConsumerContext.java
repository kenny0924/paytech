package com.paytech.acquiring.core.dal.dataobject.customer;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 消费者表对象；敏感原值不在本库保存。 */
@Getter
@Setter
public class ConsumerContext {
    /** 消费者资料主键。 */
    private long consumerContextId;
    /** 所属订单主键。 */
    private long orderId;
    /** 稳定邮箱保护引用，仅用于受控回填。 */
    private String emailVaultToken;
    /** 姓名与账单地址保护引用，仅用于受控回填。 */
    private String consumerProfileVaultToken;
    /** 规范化邮箱域名，用于已批准的 Risk 维度。 */
    private String emailDomain;
    /** 账单地址国家，不覆盖订单计税国家。 */
    private String billingCountryCode;
    /** 可选账单地址地区。 */
    private String billingRegionCode;
    /** 账单城市。 */
    private String billingCity;
    /** 资料替换时递增的本地版本。 */
    private long version;
    /** 首次保存时刻。 */
    private Instant createTime;
    /** 最近一次替换时刻。 */
    private Instant updateTime;
}
