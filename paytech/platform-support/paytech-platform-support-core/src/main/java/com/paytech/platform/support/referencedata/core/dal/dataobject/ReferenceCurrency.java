package com.paytech.platform.support.referencedata.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.Getter;
import lombok.Setter;

/** t_ref_currency 映射对象；只在 referencedata 模块内使用。 */
@Getter
@Setter
public class ReferenceCurrency {

    /**
     * 币种主键
     * <li>初始化数据使用 1001 起的固定 ID，维护新建的使用 Snowflake ID。</li>
     */
    private Long id;

    /** ISO 4217 三位币种代码。 */
    private String currencyCode;

    /**
     * ISO 4217 三位数字代码
     * <li>保留前导零。</li>
     */
    private String numericCode;

    /** ISO 4217 英文名称。 */
    private String name;

    /**
     * 货币符号
     * <li>没有专用符号时等于币种代码。</li>
     */
    private String symbol;

    /**
     * 最小货币单位位数
     * <li>0..8。</li>
     */
    private Integer minorUnit;

    /** 舍入方式。 */
    private CurrencyRoundingMode roundingMode;

    /**
     * logo 文件地址
     * <li>文件服务地址 /files/v1/{fileId}；没有 logo 时为空。</li>
     */
    private String logoFileAddress;

    /** 状态。 */
    private CurrencySpecStatus status;

    /**
     * 乐观并发版本
     * <li>新建为 1，每次成功修改递增 1。</li>
     */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
