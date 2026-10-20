package com.paytech.commercial.api.history.response;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** 一条不可变变更快照：受审计行身份、聚合版本坐标、操作者与白名单审计视图。 */
@Getter
@Setter
public class BusinessChangeSummary {
    /** 受审计行的历史身份 ID */
    private String historyId;

    /**
     * 受审计对象类型
     * <li>AGREEMENT、AGREEMENT_PRODUCT、AGREEMENT_CAPABILITY、COMMERCIAL_TERM_PLAN、COMMERCIAL_TERM_VERSION、PAYMENT_METHOD_PROFILE。</li>
     */
    private String recordType;

    /** 受审计业务行 ID（十进制） */
    private String recordId;

    /**
     * 本次变化提交后的聚合根版本
     * <li>同一命令的 Root 与 Child 快照共享同一版本；同一业务行允许跳号。</li>
     */
    private Long versionNo;

    /**
     * 行操作类型
     * <li>CREATE、UPDATE、DELETE。</li>
     */
    private String operationType;

    /** 操作者 Account ID；系统命令为 0 */
    private String createBy;

    /** 快照创建时间，UTC ISO 8601 */
    private String createTime;

    /** 触发信息 */
    private BusinessChangeMeta meta;

    /**
     * 白名单审计视图
     * <li>键与值按快照原样返回：ID / 金额 / 时间为字符串，集合为数组；不同 recordType 的键不同。</li>
     */
    private Map<String, Object> data;
}
