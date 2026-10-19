package com.paytech.channel.connector.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_channel_cost_scope：成本版本对 MID、经营主体、国家、币种、支付方式和动作的精确适用范围；不可变。 */
@Getter
@Setter
public class ChannelCostScope {
    /**
     * 渠道成本适用范围的 Snowflake 主键
     */
    private Long id;

    /**
     * 所属成本版本主键
     */
    private Long costVerId;

    /**
     * 同一版本内唯一的范围序号
     */
    private Integer scopeNo;

    /**
     * Payment Channel MID（本库 t_channel_mid.mid_id）
     * <li>写入时校验存在、归属方案 Channel 且 entity_no 一致。</li>
     */
    private Long midId;

    /**
     * Operating Entity 编号，等于 MID 归属的 OE
     */
    private String entityNo;

    /**
     * 该范围实际采用的 Channel 合同或费率文件稳定引用
     */
    private String channelAgreementRef;

    /**
     * ISO 3166-1 alpha-2 国家代码
     */
    private String countryCode;

    /**
     * ISO 4217 币种代码，也是成本金额的币种
     */
    private String currency;

    /**
     * Payment 支付方式目录代码
     */
    private String paymentMethodCode;

    /**
     * Payment 支付、退款或争议动作类别
     */
    private String actionType;

    /**
     * 该记录在本库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
