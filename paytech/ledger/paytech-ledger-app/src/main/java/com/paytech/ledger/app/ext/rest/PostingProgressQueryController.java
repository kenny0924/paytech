package com.paytech.ledger.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.PostingProgressQueryApi;
import com.paytech.ledger.api.response.PostingProgressResponseBody;
import com.paytech.ledger.app.ext.rest.converter.PostingProgressConverter;
import com.paytech.ledger.service.processor.GetPostingProgressProcessor;
import com.paytech.ledger.service.processor.request.GetPostingProgressRequest;

import lombok.AllArgsConstructor;

/** 账簿入账进度查询的 HTTP 协议适配；查询需要事务外读取 billing / account，委托 Processor。 */
@RestController
@AllArgsConstructor
public class PostingProgressQueryController implements PostingProgressQueryApi {
    private final PostingProgressConverter converter;
    private final GetPostingProgressProcessor getPostingProgressProcessor;

    /** 查询一本账簿的两段入账进度。 */
    @Override
    public PostingProgressResponseBody getPostingProgress(String ledgerBookId) {
        return converter.toResponseBody(
            getPostingProgressProcessor.process(new GetPostingProgressRequest(Long.parseLong(ledgerBookId))));
    }
}
