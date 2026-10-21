package com.paytech.reconciliation.api.exception.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 对账异常及账龄。 */
@Getter
@Setter
public class ReconExceptionResponseBody {
    /** 异常主键 */
    private String exceptionId;

    /** 异常类型，见 ReconExceptionType */
    private String exceptionType;

    /** 对账层：TRANSACTION、BANK */
    private String reconLayer;

    /** 是否阻塞所属渠道账单对平 */
    private Boolean blocking;

    /** 来源对象类型，见 ReconExceptionSourceType */
    private String sourceType;

    /** 来源对象主键 */
    private String sourceId;

    /** 交易层异常所属的渠道账单主键；无关时为空 */
    private String statementId;

    /** 清算账户主键；银行层异常为空 */
    private String clearingAccountId;

    /** 银行账户主键；交易层异常为空 */
    private String cashAccountId;

    /** 主体类型：OPERATING_ENTITY、FINANCE_ENTITY */
    private String entityType;

    /** 主体标识 */
    private String entityId;

    /** 币种 */
    private String currency;

    /**
     * 系统算出的待处置差异
     * <li>带符号，按币种精度补齐；归类金额必须与之相等。</li>
     */
    private String differenceAmount;

    /** 系统生成的说明；可为空 */
    private String detail;

    /** 状态：OPEN、UNDER_REVIEW、RESOLVED、AUTO_CLOSED */
    private String status;

    /** 人工开立人 Account ID；系统开立为空 */
    private String openedBy;

    /** 开立的 UTC 时间 */
    private Instant openedTime;

    /**
     * 账龄天数
     * <li>未结为当前 UTC 时间与开立时间相差的整天数，已结为结案时间与开立时间相差的整天数。</li>
     */
    private Integer ageDays;

    /** 结案的 UTC 时间；未结为空 */
    private Instant closedTime;

    /** 乐观锁版本 */
    private String version;
}
