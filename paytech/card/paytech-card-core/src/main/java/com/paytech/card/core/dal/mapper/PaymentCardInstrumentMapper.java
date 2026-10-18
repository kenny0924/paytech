package com.paytech.card.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;

/** 卡交易工具 SQL 映射 */
@Mapper
public interface PaymentCardInstrumentMapper {
    /** 插入新交易工具。 */
    int insertInstrument(PaymentCardInstrument instrument);

    /** 按交易工具 ID 查询。 */
    PaymentCardInstrument findInstrument(@Param("paymentCardInstrumentId") long paymentCardInstrumentId);

    /** 按 Client 与来源提交查询（两段创建的幂等键）。 */
    PaymentCardInstrument findBySubmission(@Param("clientId") long clientId,
        @Param("sourceSubmissionId") long sourceSubmissionId);

    /** 过期早于给定时间、从未签发票据且不是已存卡来源的工具 ID，按期限排列。 */
    List<Long> listOrphanIds(@Param("expiredBefore") Instant expiredBefore, @Param("limit") int limit);

    /** 条件删除一条孤立工具；条件不再成立时影响行数为 0。 */
    int deleteOrphan(@Param("paymentCardInstrumentId") long paymentCardInstrumentId,
        @Param("expiredBefore") Instant expiredBefore);
}
