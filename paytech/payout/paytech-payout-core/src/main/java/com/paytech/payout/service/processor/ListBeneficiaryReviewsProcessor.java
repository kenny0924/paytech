package com.paytech.payout.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.service.processor.request.ListBeneficiaryReviewsRequest;

import lombok.AllArgsConstructor;

/**
 * 运营核验队列（P11）：MAKER / CHECKER / 近 30 天 DONE，分页每页最多 50 条，每条带跨 Owner 共用提示
 * <li>数据库 LIMIT / OFFSET 与 COUNT 同条件；排序稳定。</li>
 */
@Component
@AllArgsConstructor
public class ListBeneficiaryReviewsProcessor
    extends AbstractProcessor<ListBeneficiaryReviewsRequest, PagingResponseBody<BeneficiaryAccountView>> {
    /** 每页上限。 */
    static final int MAX_PAGE_SIZE = 50;

    /** DONE 阶段的回看窗口。 */
    static final Duration DONE_WINDOW = Duration.ofDays(30);

    private final Clock clock;
    private final BeneficiaryAccountRepository beneficiaryAccountRepository;

    /** 每页不超过 50 条。 */
    @Override
    protected void validate(ListBeneficiaryReviewsRequest request) {
        if (request.getPaging().size() > MAX_PAGE_SIZE) {
            throw new InvalidParameterException("核验队列每页最多 " + MAX_PAGE_SIZE + " 条");
        }
    }

    /** 分页读取并组合视图。 */
    @Override
    protected PagingResponseBody<BeneficiaryAccountView> processRequest(ListBeneficiaryReviewsRequest request) {
        Instant now = clock.instant();
        Instant decidedSince = now.minus(DONE_WINDOW);
        PagingRequestBody paging = request.getPaging();
        List<BeneficiaryAccount> accounts = beneficiaryAccountRepository.findReviewPage(request.getStage(),
            request.getOwnerType(), request.getOwnerId(), decidedSince, paging.offset(), paging.size());
        long total = beneficiaryAccountRepository.countReviewPage(request.getStage(), request.getOwnerType(),
            request.getOwnerId(), decidedSince);
        List<BeneficiaryAccountView> views = accounts.stream().map(account -> new BeneficiaryAccountView(account,
            account.usableAt(now), beneficiaryAccountRepository.listSharedAccounts(account,
                BeneficiaryAccountView.SHARED_ACCOUNT_LIMIT), null)).toList();
        return new PagingResponseBody<>(views, paging.page(), paging.size(), total);
    }
}
