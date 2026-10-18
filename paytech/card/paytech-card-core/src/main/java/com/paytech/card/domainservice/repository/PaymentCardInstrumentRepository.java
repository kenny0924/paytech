package com.paytech.card.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;

/** 卡交易工具数据访问 */
public interface PaymentCardInstrumentRepository {
    /** 插入卡交易工具；交易工具 ID 由调用方分配（作为 CVV 的 AAD 与 Redis Key）。 */
    void insertInstrument(PaymentCardInstrument instrument);

    /** 按工具引用查询；引用不是有效的交易工具 ID 或记录不存在时返回 null。 */
    PaymentCardInstrument findInstrument(String instrumentRef);

    /** 按 Client 与来源提交查询；不存在时返回 null。 */
    PaymentCardInstrument findBySubmission(long clientId, long sourceSubmissionId);

    /** 过期早于给定时间、从未签发票据且不是已存卡来源的工具 ID，最多 limit 条。 */
    List<Long> listOrphanIds(Instant expiredBefore, int limit);

    /** 条件删除一条孤立工具，返回是否删除。 */
    boolean deleteOrphan(long paymentCardInstrumentId, Instant expiredBefore);
}
