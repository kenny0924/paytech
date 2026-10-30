package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;
import static com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.STREAM_ID_PATTERN;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.operations.console.bff.integration.FinanceLedgerBookClient;
import com.paytech.operations.console.bff.integration.FinanceSubledgerClient;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.CatchUpPostingStreamResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.PostingListResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.PostingProgressResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.RebuildPostingStreamsRequestBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.RebuildPostingStreamsResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.processor.PostingProgressProcessor;
import com.paytech.operations.console.bff.service.processor.request.PostingProgressRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 明细账运维的浏览器入口（块 148）：进度由 BFF 聚合，批次浏览、追平与重建简单转发
 * <li>Gateway 绑定：进度与批次浏览 FINANCE_ACCOUNTING_READ；追平与重建 FINANCE_SUBLEDGER_MANAGE，并经 PLT-I02 实时授权。</li>
 * <li>流身份 {@code {OE|FE}.{entityId}.{currency}.{shardNo}} 先按正则、再按 account-api 解析器校验并拆分；批次详情沿用明细账查询的已有路由。</li>
 * <li>追平每次最多 200 批（technique S-02），带会话操作人 Header；重建以会话 Account ID 作为 operatorId。两者使用长耗时读取超时，
 * 业务拒绝的 Owner detail 原样到达页面。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance")
public class FinancePostingOpsController {
    /** 每次追平最多处理的批次数。 */
    static final int CATCH_UP_MAX_BATCHES = 200;

    /** 批次浏览允许的每页批次数。 */
    static final Set<Integer> POSTING_PAGE_LIMITS = Set.of(20, 50, 100, 200);

    /** 批次浏览默认每页批次数。 */
    static final int DEFAULT_POSTING_PAGE_LIMIT = 50;

    private static final String ID = "[1-9][0-9]{0,18}";

    private final PostingProgressProcessor progressProcessor;
    private final FinanceLedgerBookClient ledgerClient;
    private final FinanceSubledgerClient subledgerClient;

    /** B1 账簿入账进度：两段判定、各流两侧序号，落后的流带卡住的批次。 */
    @GetMapping("/posting-progress/v1/ledger-books/{ledgerBookId}")
    public PostingProgressResponseBody getPostingProgress(HttpServletRequest servletRequest,
        @PathVariable("ledgerBookId") @Pattern(regexp = ID) String ledgerBookId) {
        requireQueryKeys(servletRequest, Set.of());
        return progressProcessor.process(new PostingProgressRequest(ledgerBookId));
    }

    /** B2 过账流批次浏览：从起始序号按序号升序读取批次摘要（不含流水）。 */
    @GetMapping("/posting-streams/v1/{streamId}/postings")
    public PostingListResponseBody listStreamPostings(HttpServletRequest servletRequest,
        @PathVariable("streamId") @Pattern(regexp = STREAM_ID_PATTERN) String streamId,
        @RequestParam("fromSequence") @Pattern(regexp = ID) String fromSequence,
        @RequestParam(value = "limit", required = false) Integer limit) {
        requireQueryKeys(servletRequest, Set.of("fromSequence", "limit"));
        int pageLimit = limit == null ? DEFAULT_POSTING_PAGE_LIMIT : limit;
        if (!POSTING_PAGE_LIMITS.contains(pageLimit)) {
            throw new InvalidParameterException("每页批次数只能为 20、50、100 或 200");
        }
        return subledgerClient.listStreamPostings(stream(streamId), Long.parseLong(fromSequence), pageLimit);
    }

    /** B3 追平一条过账流；无请求体，携带 Body 时被请求边界拒绝。 */
    @PostMapping("/posting-streams/v1/{streamId}/catch-up")
    public CatchUpPostingStreamResponseBody catchUpPostingStream(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("streamId") @Pattern(regexp = STREAM_ID_PATTERN) String streamId) {
        requireQueryKeys(servletRequest, Set.of());
        return ledgerClient.catchUpPostingStream(context, stream(streamId), CATCH_UP_MAX_BATCHES);
    }

    /** B4 按过账流重建主体与币种下的 Owner 账户余额。 */
    @PostMapping("/posting-rebuilds/v1")
    public RebuildPostingStreamsResponseBody rebuildPostingStreams(HttpServletRequest servletRequest,
        OperatorContext context, @RequestBody @NotNull @Valid RebuildPostingStreamsRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return subledgerClient.rebuildPostingStreams(context, body);
    }

    /** 流身份按 account-api 解析器拆分；格式正确但不可解析（如超长）时为参数错误。 */
    static AccountPostedPayload.StreamKey stream(String streamId) {
        try {
            return AccountPostedPayload.parseAggregateId(streamId);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidParameterException("过账流身份非法: " + streamId);
        }
    }
}
