package com.paytech.ledger.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.ledger.api.response.PostingProgressResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 账簿入账进度查询（内部能力 Ledger & Account Balance）
 * <li>按账簿给出 billing → account（计费事实）与 account → ledger（过账流消费）两段进度；判定与期间关账步骤
 * CHECK_ACCOUNT_POSTING_PROGRESS 共用同一段逻辑，页面结论与关账结论一致。</li>
 * <li>只读，不开显式事务、不加锁；billing / account 进度在事务外读取，已消费序号按流以本库短事务读取。</li>
 * <li>Finance 内部接口，不带操作人；经 Operations 控制台"明细账运维"页由运营 BFF 调用。</li>
 */
@HttpExchange(url = "/posting-progress/v1", accept = "application/json")
public interface PostingProgressQueryApi {

    /**
     * 查询一本账簿的两段入账进度
     *
     * @param ledgerBookId 账簿主键，十进制字符串
     * @return 计费事实段与过账流段的数字和判定；
     *         <li>billing 或 account 不可用时不报错：计费事实段为 UNAVAILABLE；account 不可用时过账流段同为 UNAVAILABLE 且 streams 为空。</li>
     *         <li>账簿不存在 400 LEDGER_BOOK_NOT_FOUND。</li>
     */
    @GetExchange("/ledger-books/{ledgerBookId}")
    PostingProgressResponseBody getPostingProgress(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId);
}
