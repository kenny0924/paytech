package com.paytech.acquiring.service.processor.order;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.service.processor.order.request.ExpireOrderRequest;
import com.paytech.acquiring.service.processor.order.request.ScanExpiredOrdersRequest;
import com.paytech.acquiring.service.processor.order.response.ExpireOrderResponse;
import com.paytech.acquiring.service.processor.order.response.ScanExpiredOrdersResponse;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 按分片扫描到期仍为 CREATED/OPEN 的订单，逐单调用过期收尾。
 * <li>按 order_id 游标分页，最多读取 maxBatches 页；本次未收尾的订单留待下一轮，游标前移不会重复处理。</li>
 * <li>每单独立加锁和提交；并发冲突（订单锁被占用或写入时版本已变）计为跳过，其他异常记录日志计为失败，均不影响同批其余订单。</li>
 * <li>本身不加锁、不开事务；多实例重复扫描由收尾的订单锁与条件更新保证只写一次。</li>
 */
@Component
@AllArgsConstructor
public class ScanExpiredOrdersProcessor extends AbstractProcessor<ScanExpiredOrdersRequest, ScanExpiredOrdersResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(ScanExpiredOrdersProcessor.class);

    private final ExpireOrderProcessor expireOrderProcessor;
    private final CommerceOrderRepository commerceOrderRepository;

    @Override
    protected void validate(ScanExpiredOrdersRequest request) {
        if (request.getShardIndex() >= request.getShardTotal()) {
            throw new InvalidParameterException("分片序号必须小于分片总数");
        }
    }

    @Override
    protected ScanExpiredOrdersResponse processRequest(ScanExpiredOrdersRequest request) {
        var response = new ScanExpiredOrdersResponse();
        long afterOrderId = 0;
        for (int batch = 0; batch < request.getMaxBatches(); batch++) {
            List<Long> orderIds = commerceOrderRepository.findExpiredOrderIds(request.getNow(), afterOrderId,
                request.getShardIndex(), request.getShardTotal(), request.getBatchSize());
            orderIds.forEach(orderId -> expireOrder(orderId, request, response));
            if (orderIds.size() < request.getBatchSize()) {
                break;
            }
            afterOrderId = orderIds.getLast();
        }
        return response;
    }

    /**
     * 收尾一单并记录结果；单单失败不中断扫描
     * <li>并发冲突说明另一实例或其他入口正在处理该订单，本轮跳过。</li>
     * <li>其余运行时异常（数据不一致、Payment 拒绝或协议错误）记录后计为失败，下一轮仍会再选中。</li>
     */
    void expireOrder(long orderId, ScanExpiredOrdersRequest request, ScanExpiredOrdersResponse response) {
        try {
            response.recordResult(expireOrderProcessor.process(new ExpireOrderRequest(orderId, request.getNow(),
                request.getPendingAlertAfter())));
        } catch (ConcurrentRequestException concurrent) {
            response.recordResult(ExpireOrderResponse.SKIPPED);
        } catch (RuntimeException failure) {
            LOG.warn("Order expiry failed and will be retried in the next scan, orderId={}", orderId, failure);
            response.recordFailure();
        }
    }
}
