package com.paytech.commerce.domainservice.repository.refund;

import java.time.Instant;
import java.util.List;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequestView;
import com.paytech.commerce.domain.enums.RefundStatus;

/** 退款表的查询与状态写入边界，金额位置由独立仓储维护。 */
public interface RefundRepository {
    /** 按 Client 业务退款号读取本表记录。 */
    RefundRequest findByClientRefundId(long clientId, String clientRefundId);

    /** 按可信 Client 和公开退款号读取本表记录。 */
    RefundRequest findByRefundRequestNo(long clientId, String refundRequestNo);

    /** 对外查询必须传 Client；仅内部恢复可以按全局退款号查询。 */
    RefundRequestView find(Long clientId, String no, String clientRefundId, boolean lock);
    /** 单表查询同一 Client 和订单的退款页，不关联订单或支付数据。 */
    List<RefundRequest> list(long clientId, long orderId, Instant time, String no, int limit);
    /** 插入已构造的退款记录。 */
    void insert(RefundRequest refund);
    /** 条件更新后同步原退款对象。 */
    void update(RefundRequest refund, RefundStatus status, Long channelId, String reason,
            Instant completedAt, Instant now);
}
