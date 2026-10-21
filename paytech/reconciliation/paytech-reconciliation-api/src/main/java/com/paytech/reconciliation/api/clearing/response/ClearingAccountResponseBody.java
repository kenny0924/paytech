package com.paytech.reconciliation.api.clearing.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 清算账户详情：配置与已挂入的 MID。 */
@Getter
@Setter
public class ClearingAccountResponseBody {
    /** 清算账户主键 */
    private String clearingAccountId;

    /** 清算账户编码 */
    private String clearingAccountCode;

    /** 渠道编码 */
    private String channelCode;

    /** 经营主体编号 */
    private String entityNo;

    /** 清算币种 */
    private String currency;

    /**
     * 到账银行账户主键
     * <li>未配置为空。</li>
     */
    private String settlementCashAccountId;

    /** 账单行未命中等待天数 */
    private Integer unmatchedWaitDays;

    /**
     * 实际渠道费容差
     * <li>按币种精度补齐小数位。</li>
     */
    private String costToleranceAmount;

    /** 预期银行流水到账宽限天数 */
    private Integer cashGraceDays;

    /** 状态：ACTIVE、INACTIVE */
    private String status;

    /** 乐观锁版本 */
    private String version;

    /** 创建的 UTC 时间 */
    private Instant createTime;

    /** 最后更新的 UTC 时间 */
    private Instant updateTime;

    /** 已挂入的 MID，按挂入时间升序 */
    private List<Mid> mids;

    /** 清算账户所含的 MID。 */
    @Getter
    @Setter
    public static class Mid {
        /** channel-connector MID 主键 */
        private String midId;

        /** 挂入的 UTC 时间 */
        private Instant createTime;
    }
}
