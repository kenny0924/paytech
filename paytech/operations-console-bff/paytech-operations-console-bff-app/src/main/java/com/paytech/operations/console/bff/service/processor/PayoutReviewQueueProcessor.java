package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PayoutClient;
import com.paytech.operations.console.bff.service.model.payout.response.PayoutReviewItemResponseBody;
import com.paytech.operations.console.bff.service.processor.request.PayoutReviewQueueRequest;

import lombok.AllArgsConstructor;

/**
 * 收款账户核验队列：payout 队列分页 + 每行 Owner 法定名与共用同一账号的其他 Owner 名称
 * <li>payout 失败整页失败；Owner 主档读取失败只让该行 owner 为空。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PayoutReviewQueueProcessor
    extends AbstractProcessor<PayoutReviewQueueRequest, PagingResponseBody<PayoutReviewItemResponseBody>> {
    private final PayoutClient payout;
    private final PayoutOwnerResolver owners;

    @Override
    protected PagingResponseBody<PayoutReviewItemResponseBody> processRequest(PayoutReviewQueueRequest request) {
        var accounts = payout.listReviews(request.getStage(), request.getOwnerType(), request.getOwnerId(),
            request.getPage(), request.getSize());
        var resolved = owners.resolve(request.getOperator(), accounts.items().stream().flatMap(PayoutOwnerResolver::keys));
        return accounts.map(item -> new PayoutReviewItemResponseBody(item,
            resolved.get(PayoutOwnerResolver.key(item.getOwnerType(), item.getOwnerId())),
            PayoutOwnerResolver.sharedProfiles(item, resolved)));
    }
}
