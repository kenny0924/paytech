package com.paytech.client.master.service.processor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.APPLICATION_NOT_EDITABLE;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.INVALID_DRAFT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import com.paytech.client.master.api.onboarding.response.DeclarationResponseBody;
import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.domain.enums.RequirementLevel;
import com.paytech.client.master.domain.enums.WidgetCode;
import com.paytech.client.master.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.master.domainservice.repository.ApplicationDeclarationRepository;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.service.ClientApplicationCommandService;
import com.paytech.client.master.domainservice.service.OnboardingPackService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.RecordDeclarationRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * CM-6 记录 Client 声明：账号、时间、可信 IP 与 User-Agent 作为电子声明证据，只追加
 * <li>与草稿写入共用申请锁；只在 DRAFT/RETURNED 可写；声明类型须为申请冻结 Schema 的 declaration.<类型> 字段。</li>
 * <li>同类型的最新一条取值与条款版本都相同时返回原记录，不重复追加。</li>
 */
@AllArgsConstructor
public class RecordApplicationDeclarationProcessor
    extends AbstractLockProcessor<RecordDeclarationRequest, DeclarationResponseBody> {
    private final Clock clock;
    private final IdGenerator ids;
    private final Duration lockWait;
    private final Duration lockLease;
    private final OnboardingPackService packs;
    private final ClientDraftRepository drafts;
    private final ClientDraftAccountClient accounts;
    private final ApplicationDeclarationRepository declarations;
    private final ClientApplicationCommandService commands;

    /** 与草稿写入同一申请锁。 */
    @Override
    protected LockCommand lockCommand(RecordDeclarationRequest request) {
        return LockCommand.tryLocks(List.of(ClientOnboardingLocks.application(request.getApplicationId())), lockWait, lockLease);
    }

    /** 归属与可编辑 → 类型在 Schema 中 → 同内容返回原记录，否则追加。 */
    @Override
    protected DeclarationResponseBody processWithinLock(RecordDeclarationRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        ClientDraftIdentity application = ClientDraftPolicy.requireValid(
            drafts.identity(request.getApplicationId(), request.getAccountId(), false));
        if (!ClientDraftPolicy.isEditable(application)) {
            throw new FailException(APPLICATION_NOT_EDITABLE);
        }
        requireSchemaDeclaration(application, request.getDeclarationType());
        ApplicationDeclaration latest = declarations.findByApplication(application.applicationId()).stream()
            .filter(row -> row.getDeclarationType().equals(request.getDeclarationType())).reduce((first, second) -> second).orElse(null);
        if (latest != null && latest.getDeclaredValue().equals(request.getDeclaredValue())
            && Objects.equals(latest.getTermsVersion(), request.getTermsVersion())) {
            return response(latest);
        }
        ApplicationDeclaration row = declaration(application, request);
        commands.declare(row);
        return response(row);
    }

    /** 声明类型须为申请冻结 Schema 中未隐藏的 declaration.<类型> 字段。 */
    void requireSchemaDeclaration(ClientDraftIdentity application, String type) {
        boolean known = packs.load(application.schemaId()).getFields().stream()
            .anyMatch(field -> field.getWidgetCode() == WidgetCode.DECLARATION && field.getRequirementLevel() != RequirementLevel.HIDDEN
                && field.getFieldKey().equals("declaration." + type));
        if (!known) {
            throw new RequestBodyValidationException(INVALID_DRAFT,
                List.of(new ProblemFieldError("declarationType", "当前申请不需要此声明")), null);
        }
    }

    /** 新声明行；User-Agent 超长截断。 */
    ApplicationDeclaration declaration(ClientDraftIdentity application, RecordDeclarationRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var row = new ApplicationDeclaration();
        row.setDeclarationId(ids.nextId());
        row.setClientApplicationId(application.applicationId());
        row.setDeclarationType(request.getDeclarationType());
        row.setDeclaredValue(request.getDeclaredValue());
        row.setDeclaredByAccountId(request.getAccountId());
        row.setDeclaredTime(now);
        row.setClientIp(request.getClientIp() == null || request.getClientIp().length() > 64 ? null : request.getClientIp());
        row.setUserAgent(request.getUserAgent() == null ? null
            : request.getUserAgent().substring(0, Math.min(512, request.getUserAgent().length())));
        row.setTermsVersion(request.getTermsVersion());
        row.setCreateTime(now);
        return row;
    }

    /** 声明响应。 */
    DeclarationResponseBody response(ApplicationDeclaration row) {
        var body = new DeclarationResponseBody();
        body.setDeclarationId(row.getDeclarationId().toString());
        body.setApplicationId(row.getClientApplicationId().toString());
        body.setDeclarationType(row.getDeclarationType());
        body.setDeclaredValue(row.getDeclaredValue());
        body.setTermsVersion(row.getTermsVersion());
        body.setDeclaredAt(new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(row.getDeclaredTime()));
        return body;
    }
}
