package com.paytech.billing.api.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 计费事实管理列表的一条事实头摘要；明细行、引用与发布状态从详情接口读取。 */
@Getter
@Setter
public class BillingFactSummaryResponseBody {
    /** 计费事实 ID，十进制字符串 */
    private String factId;

    /** 事实类型代码（BillingFactType） */
    private String factType;

    /**
     * 来源引用
     * <li>人工登记为提交时的 sourceReference；系统事实为来源事件键。</li>
     */
    private String sourceReference;

    /**
     * CCT Client 稳定业务 ID
     * <li>资金类事实为空。</li>
     */
    private String clientId;

    /** 登记状态：DRAFT、APPROVED、SUPERSEDED、VOIDED */
    private String status;

    /** 事实总额，币种主单位，按币种精度补齐 */
    private String grossAmount;

    /**
     * 编制人 Operations Account ID
     * <li>AUTO 事实为空。</li>
     */
    private String makerId;

    /**
     * 复核人 Operations Account ID
     * <li>未批准、已作废或 AUTO 事实为空。</li>
     */
    private String checkerId;

    /** 登记（提交）的 UTC 时间 */
    private Instant createTime;

    /**
     * 批准的 UTC 时间
     * <li>DRAFT、VOIDED 为空。</li>
     */
    private Instant approvedTime;
}
