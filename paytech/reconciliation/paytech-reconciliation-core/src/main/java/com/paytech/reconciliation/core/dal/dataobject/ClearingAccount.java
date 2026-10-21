package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.reconciliation.api.enums.ClearingAccountStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_clearing_account（清算账户）
 * <li>渠道清算账户：一个经营主体在一个渠道、一个币种下与渠道结算的边界，平台流水与渠道账单都挂在它下面；与渠道技术账号无关。</li>
 */
@Getter
@Setter
public class ClearingAccount {
    /**
     * 清算账户ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 清算账户编码
     * <li>运营可读的清算账户编码，渠道账单文件用它指明所属清算账户；创建后不可改。</li>
     */
    private String clearingAccountCode;

    /**
     * 渠道编码
     * <li>渠道编码，挂入的 MID 必须属于该渠道。取值来源：channel-connector 渠道目录；非固定枚举。</li>
     */
    private String channelCode;

    /**
     * 经营主体编号
     * <li>与渠道签约的经营主体编号，挂入的 MID 必须属于该主体。</li>
     */
    private String entityNo;

    /**
     * 币种
     * <li>清算币种；渠道账单币种必须与之相同。取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 到账银行账户ID
     * <li>渠道净额打入或扣出的本库银行账户；为空时账单对平后不能生成预期银行流水，对平被拦下。</li>
     */
    private Long settlementCashAccountId;

    /**
     * 未匹配等待天数
     * <li>账单行未命中平台流水时的等待天数，期满转为 CHANNEL_LINE_UNMATCHED 异常；非负。</li>
     */
    private Integer unmatchedWaitDays;

    /**
     * 成本容差
     * <li>单笔平台流水实际渠道费与预估之差的容差，超过开 COST_DEVIATION 异常；非负。</li>
     */
    private BigDecimal costToleranceAmount;

    /**
     * 到账宽限天数
     * <li>预期银行流水超过预期日多少天仍未到账视为逾期（超期检测在切片 2 使用）；非负。</li>
     */
    private Integer cashGraceDays;

    /**
     * 状态
     * <li>停用后不再接收新的平台流水和渠道账单。允许值：ACTIVE、INACTIVE。</li>
     */
    private ClearingAccountStatus status;

    /**
     * 乐观锁版本
     * <li>状态、配置与所含 MID 变更的乐观锁版本。</li>
     */
    private Long version;

    /**
     * 创建时间
     * <li>创建的 UTC 微秒时间。</li>
     */
    private Instant createTime;

    /**
     * 更新时间
     * <li>最后更新的 UTC 微秒时间。</li>
     */
    private Instant updateTime;
}
