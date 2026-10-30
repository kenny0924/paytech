package com.paytech.payout.api.beneficiary;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 运营收款账户核验队列（technique 8.1 P11，调用方 Operations BFF）
 * <li>MAKER：PENDING_REVIEW 且尚无 Maker 意见；CHECKER：PENDING_REVIEW 且已有 Maker 意见；DONE：近 30 天已有复核决定。</li>
 * <li>每页最多 50 条；页签计数取各阶段响应的 total。</li>
 */
@HttpExchange(url = "/payout/v1/beneficiary-reviews", accept = "application/json")
public interface BeneficiaryReviewApi {

    /**
     * 分页查询核验队列
     *
     * @param stage 阶段：MAKER、CHECKER、DONE
     * @param ownerType 可选 Owner 类型
     * @param ownerId 可选 Owner 标识，与 ownerType 同时给出
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50
     * @return 账户分页（含 sharedOwners 与 reviewMakerNote）；MAKER / CHECKER 按提交时间升序，DONE 按决定时间降序；参数非法 400
     */
    @GetExchange
    PagingResponseBody<BeneficiaryAccountResponseBody> listBeneficiaryReviews(
        @RequestParam("stage") @NotBlank @Pattern(regexp = "MAKER|CHECKER|DONE") String stage,
        @RequestParam(value = "ownerType", required = false) @Pattern(regexp = "CLIENT|LOCAL_PARTNER") String ownerType,
        @RequestParam(value = "ownerId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String ownerId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
