package com.paytech.client.console.bff.service.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 最近一次审核的客户可见结果
 * <li>只在 CCT 状态为 RETURNED/APPROVED/REJECTED 时读取；不含内部理由、核验记录或决定人。</li>
 */
@Getter
@AllArgsConstructor
public class ReviewResult {

    /** 审核结论，与 CCT 申请状态一致 */
    private final String outcome;

    /**
     * 退回分区：BASIC、BUSINESS、PEOPLE 或 MATERIALS
     * <li>只在退回时有值。</li>
     */
    private final String returnSection;

    /** 给客户的公开说明 */
    private final String publicNote;

    /** 决定时间，UTC 六位微秒字符串 */
    private final String decidedAt;
}
