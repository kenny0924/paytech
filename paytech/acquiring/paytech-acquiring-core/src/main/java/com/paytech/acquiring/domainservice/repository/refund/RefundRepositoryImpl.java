package com.paytech.acquiring.domainservice.repository.refund;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.acquiring.core.dal.dataobject.refund.RefundRequest;
import com.paytech.acquiring.core.dal.dataobject.refund.RefundRequestView;
import com.paytech.acquiring.core.dal.mapper.refund.RefundMapper;
import com.paytech.acquiring.domain.enums.RefundStatus;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** 唯一索引和条件更新是退款并发的最终防线。 */
@Repository
@AllArgsConstructor
public class RefundRepositoryImpl implements RefundRepository {
    private final RefundMapper mapper;

    /** 创建只读取退款单表，不为返回订单号额外联表。 */
    @Override
    public RefundRequest findByClientRefundId(long clientId, String clientRefundId) {
        return mapper.findByClientRefundId(clientId, clientRefundId);
    }

    /** 按当前 Client 的公开退款号读取本表，供详情查询或游标锚点校验。 */
    @Override
    public RefundRequest findByRefundRequestNo(long clientId, String refundRequestNo) {
        return mapper.findByRefundRequestNo(clientId, refundRequestNo);
    }

    /** 按调用范围读取退款与关联订单号。 */
    @Override
    public RefundRequestView find(Long clientId, String no, String clientRefundId, boolean lock) {
        return mapper.select(clientId, no, clientRefundId, lock);
    }

    /** 读取订单下的退款查询页。 */
    @Override
    public List<RefundRequest> list(long clientId, long orderId, Instant time, String no, int limit) {
        return mapper.list(clientId, orderId, time, no, limit);
    }

    /** 插入退款事实，失败由 Service 回滚。 */
    @Override
    public void insert(RefundRequest refund) {
        requireOne(mapper.insert(refund));
    }

    /** 条件写入成功后同步原退款对象。 */
    @Override
    public void update(RefundRequest refund, RefundStatus status, Long channelId, String reason, Instant completedAt,
        Instant now) {
        requireOne(mapper.update(refund, status, channelId, reason, completedAt, now));
        refund.setRefundStatus(status);
        refund.setChannelRefundId(channelId);
        refund.setStatusReasonCode(reason);
        refund.setCompletedAt(completedAt);
        refund.setVersion(refund.getVersion() + 1);
        refund.setUpdateTime(now);
    }

    /** 不符合预期行数时终止本地事务。 */
    private void requireOne(int changed) {
        if (changed != 1) {
            throw new ConcurrentRequestException("退款状态或金额位置已改变");
        }
    }
}
