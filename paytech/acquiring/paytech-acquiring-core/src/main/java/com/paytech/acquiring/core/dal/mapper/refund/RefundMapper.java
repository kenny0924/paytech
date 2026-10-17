package com.paytech.commerce.core.dal.mapper.refund;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequestView;
import com.paytech.commerce.domain.enums.RefundStatus;

/** 退款表 SQL；需要订单号的查询使用两表 View。 */
public interface RefundMapper {
    /** 创建入口按业务唯一身份读取退款单表。 */
    RefundRequest findByClientRefundId(@Param("clientId") long clientId,
        @Param("clientRefundId") String clientRefundId);

    /** 在可信 Client 范围内按公开退款号查询本表。 */
    RefundRequest findByRefundRequestNo(@Param("clientId") long clientId,
        @Param("refundRequestNo") String refundRequestNo);

    /** 按归属读取退款和订单号。 */
    RefundRequestView select(@Param("clientId") Long clientId, @Param("no") String no,
            @Param("clientRefundId") String clientRefundId, @Param("lock") boolean lock);
    /** 查询当前订单下的退款分页。 */
    List<RefundRequest> list(@Param("clientId") long clientId, @Param("orderId") long orderId,
            @Param("time") Instant time, @Param("no") String no, @Param("limit") int limit);
    /** 插入退款记录。 */
    int insert(RefundRequest refund);
    /** 按原版本更新退款状态。 */
    int update(@Param("refund") RefundRequest refund, @Param("status") RefundStatus status,
            @Param("channelId") Long channelId, @Param("reason") String reason,
            @Param("completedAt") Instant completedAt, @Param("now") Instant now);
}
