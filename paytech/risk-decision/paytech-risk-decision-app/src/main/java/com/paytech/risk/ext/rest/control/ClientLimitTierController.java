package com.paytech.risk.ext.rest.control;

import static com.paytech.risk.service.convert.LimitTierExceptionConverter.mapped;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.foundation.web.request.OperationRequestContextParser;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.domain.limittier.LimitTierListFilter;
import com.paytech.risk.control.service.ClientLimitTierService;
import com.paytech.risk.control.service.processor.ApproveClientTierChangeProcessor;
import com.paytech.risk.control.service.processor.MigrateClientTiersProcessor;
import com.paytech.risk.control.service.processor.RejectClientTierChangeProcessor;
import com.paytech.risk.control.service.processor.RequestClientTierChangeProcessor;
import com.paytech.risk.control.service.processor.WithdrawClientTierChangeProcessor;
import com.paytech.risk.control.service.processor.request.DecideClientTierChangeRequest;
import com.paytech.risk.control.service.processor.request.MigrateClientTiersRequest;
import com.paytech.risk.control.service.processor.request.RequestClientTierChangeRequest;
import com.paytech.risk.control.service.processor.request.WithdrawClientTierChangeRequest;
import com.paytech.risk.decision.api.control.ClientLimitTierApi;
import com.paytech.risk.decision.api.control.request.DecideClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.MigrateClientTiersRequestBody;
import com.paytech.risk.decision.api.control.request.RequestClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.WithdrawClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeListResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierDetailResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierSummaryBody;
import com.paytech.risk.decision.api.control.response.TierMigrationResponseBody;
import com.paytech.risk.decision.api.control.response.TierQuotaListResponseBody;
import com.paytech.risk.decision.api.control.response.TierStatisticsResponseBody;

import lombok.AllArgsConstructor;

/** 只解析可信上下文和协议字段；写操作由 risk-control 的档位 Processor 执行，查询委托档位 Service。 */
@RestController
@AllArgsConstructor
public class ClientLimitTierController implements ClientLimitTierApi {
    /** 关键字最长字符数。 */
    static final int KEYWORD_MAX_LENGTH = 100;

    private final Clock clock;
    private final ClientLimitTierService service;
    private final MigrateClientTiersProcessor migrateProcessor;
    private final OperationRequestContextParser context;
    private final RejectClientTierChangeProcessor rejectProcessor;
    private final ApproveClientTierChangeProcessor approveProcessor;
    private final RequestClientTierChangeProcessor requestProcessor;
    private final WithdrawClientTierChangeProcessor withdrawProcessor;

    /** 分页查询 Client 档位。 */
    @Override
    public PagingResponseBody<ClientTierSummaryBody> listClientTiers(String actorAccountId, String sessionId, String tier,
        Boolean pendingApproval, Boolean observationDue, String keyword, Integer page, Integer size) {
        context.parse(actorAccountId, sessionId);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        var result = service.findPage(filter(tier, pendingApproval, observationDue, keyword), paging.size(),
            paging.offset());
        return new PagingResponseBody<>(result.items().stream().map(LimitTierWireConverter::summary).toList(),
            paging.page(), paging.size(), result.total());
    }

    /** 读取 Client 当前档位详情。 */
    @Override
    public ClientTierDetailResponseBody getClientTier(String actorAccountId, String sessionId, String clientId) {
        context.parse(actorAccountId, sessionId);
        long id = context.parsePositiveId("clientId", clientId);
        return LimitTierWireConverter.detail(service.findDetail(id, clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    /** 读取 Client 的档位变更历史。 */
    @Override
    public ClientTierChangeListResponseBody listClientTierChanges(String actorAccountId, String sessionId,
        String clientId) {
        context.parse(actorAccountId, sessionId);
        return LimitTierWireConverter.history(service.findHistory(context.parsePositiveId("clientId", clientId)));
    }

    /** 提交档位变更申请，成功返回 201。 */
    @Override
    @ResponseStatus(HttpStatus.CREATED)
    public ClientTierChangeResponseBody requestClientTierChange(String actorAccountId, String sessionId, String clientId,
        RequestClientTierChangeRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new RequestClientTierChangeRequest(actor.getActorAccountId(), context.parsePositiveId("clientId", clientId),
            body.getExpectedTierVersion(), LimitTierWireConverter.tier(body.getToTier(), "toTier"), body.getReason(),
            body.getGoLiveNote(), body.getObservationTargetDays(), body.getObservationTargetTransactions(),
            LimitTierWireConverter.checklist(body.getChecklist()));
        return LimitTierWireConverter.change(mapped(() -> requestProcessor.process(request)));
    }

    /** 申请人撤回待审批申请。 */
    @Override
    public ClientTierChangeResponseBody withdrawClientTierChange(String actorAccountId, String sessionId, String changeId,
        WithdrawClientTierChangeRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new WithdrawClientTierChangeRequest(actor.getActorAccountId(),
            context.parsePositiveId("changeId", changeId), body.getExpectedVersion());
        return LimitTierWireConverter.change(mapped(() -> withdrawProcessor.process(request)));
    }

    /** 普通审批通过。 */
    @Override
    public ClientTierChangeResponseBody approveClientTierChange(String actorAccountId, String sessionId, String changeId,
        DecideClientTierChangeRequestBody body) {
        var request = decision(actorAccountId, sessionId, changeId, body, false);
        return LimitTierWireConverter.change(mapped(() -> approveProcessor.process(request)));
    }

    /** 高级审批通过。 */
    @Override
    public ClientTierChangeResponseBody approveClientTierChangeElevated(String actorAccountId, String sessionId,
        String changeId, DecideClientTierChangeRequestBody body) {
        var request = decision(actorAccountId, sessionId, changeId, body, true);
        return LimitTierWireConverter.change(mapped(() -> approveProcessor.process(request)));
    }

    /** 普通驳回。 */
    @Override
    public ClientTierChangeResponseBody rejectClientTierChange(String actorAccountId, String sessionId, String changeId,
        DecideClientTierChangeRequestBody body) {
        var request = decision(actorAccountId, sessionId, changeId, body, false);
        return LimitTierWireConverter.change(mapped(() -> rejectProcessor.process(request)));
    }

    /** 高级驳回。 */
    @Override
    public ClientTierChangeResponseBody rejectClientTierChangeElevated(String actorAccountId, String sessionId,
        String changeId, DecideClientTierChangeRequestBody body) {
        var request = decision(actorAccountId, sessionId, changeId, body, true);
        return LimitTierWireConverter.change(mapped(() -> rejectProcessor.process(request)));
    }

    /** 读取四档 × 币种额度。 */
    @Override
    public TierQuotaListResponseBody listTierQuotas(String actorAccountId, String sessionId) {
        context.parse(actorAccountId, sessionId);
        return LimitTierWireConverter.quotas(service.quotas());
    }

    /** 读取列表顶部统计。 */
    @Override
    public TierStatisticsResponseBody getTierStatistics(String actorAccountId, String sessionId) {
        context.parse(actorAccountId, sessionId);
        return LimitTierWireConverter.statistics(service.statistics(clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    /** 存量迁移（内部接口），调用方白名单在 Processor 校验。 */
    @Override
    public TierMigrationResponseBody migrateClientTiers(String callerService, MigrateClientTiersRequestBody body) {
        var request = new MigrateClientTiersRequest(callerService, Long.parseLong(body.getExecutedBy()), body.getReason(),
            body.getClients().stream().map(LimitTierWireConverter::client).toList());
        return LimitTierWireConverter.migration(mapped(() -> migrateProcessor.process(request)));
    }

    /** 审批与驳回的公共输入。 */
    DecideClientTierChangeRequest decision(String actorAccountId, String sessionId, String changeId,
        DecideClientTierChangeRequestBody body, boolean elevated) {
        var actor = context.parse(actorAccountId, sessionId);
        return new DecideClientTierChangeRequest(actor.getActorAccountId(), context.parsePositiveId("changeId", changeId),
            body.getExpectedVersion(), body.getComment(), elevated);
    }

    /** 解析列表筛选；未知档位与超长关键字为参数错误。 */
    LimitTierListFilter filter(String tier, Boolean pendingApproval, Boolean observationDue, String keyword) {
        String text = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (text != null && text.length() > KEYWORD_MAX_LENGTH) {
            throw new InvalidParameterException("keyword 最长 " + KEYWORD_MAX_LENGTH + " 字符");
        }
        LimitTier parsed = UnderwritingWireConverter.code(LimitTier.values(), context.parseOptionalUpperCode("tier", tier),
            "tier");
        return new LimitTierListFilter(parsed, Boolean.TRUE.equals(pendingApproval), Boolean.TRUE.equals(observationDue),
            text, text != null && text.matches("[1-9][0-9]{0,17}") ? Long.valueOf(text) : null,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
