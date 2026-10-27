package com.paytech.platform.support.api.referencedata.response;

import lombok.Getter;
import lombok.Setter;

/** 参考数据目录中的一个 MCC（Merchant Category Code）；字段与 t_ref_mcc 的业务列一致。 */
@Getter
@Setter
public class MccResponseBody {

    /**
     * MCC 编码
     * <li>ISO 18245 四位数字，保留前导零，例如 5411。</li>
     */
    private String mccCode;

    /**
     * 英文名称
     * <li>例如 Grocery Stores, Supermarkets。</li>
     */
    private String name;

    /**
     * 英文说明
     * <li>最长 512 字符；没有时为空。</li>
     */
    private String description;

    /**
     * 是否卡组织公布的高风险类目
     * <li>true 表示 Visa 或 Mastercard 公布为高风险的 MCC。</li>
     */
    private Boolean networkHighRisk;

    /**
     * 状态
     * <li>{@code ReferenceDataStatus} Code：DRAFT、ACTIVE、INACTIVE；只有 ACTIVE 允许被新业务选择。</li>
     */
    private String status;

    /**
     * 乐观并发版本
     * <li>正整数，从 1 开始。</li>
     */
    private Integer version;
}
