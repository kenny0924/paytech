package com.paytech.client.master.ext.rest;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.contract.api.onboarding.ClientClassificationAdminApi;
import com.paytech.client.contract.api.onboarding.request.ClassificationSetRequestBody;
import com.paytech.client.contract.api.onboarding.response.CatalogOptionResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationItemResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationOptionsResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationSetResponseBody;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domainservice.repository.CatalogItemRepository;
import com.paytech.client.master.service.processor.ConfirmClientClassificationProcessor;
import com.paytech.client.master.service.processor.GetClientClassificationProcessor;
import com.paytech.client.master.service.processor.ReplaceClientClassificationProcessor;
import com.paytech.client.master.service.processor.request.ClientClassificationRequest;
import com.paytech.client.master.service.processor.response.ClientClassificationResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * O03/O04 及分类读取的私网协议适配；操作者来自 Gateway 可信头，只作记录
 * <li>与草稿、补偿开关同时开启时注册。</li>
 */
@RestController
@ConditionalOnProperty(prefix = "onboarding", name = {"draft-enabled", "recovery-enabled"}, havingValue = "true")
public class ClientClassificationAdminController implements ClientClassificationAdminApi {
    private final CatalogItemRepository catalog;
    private final GetClientClassificationProcessor reads;
    private final ReplaceClientClassificationProcessor replaces;
    private final ConfirmClientClassificationProcessor confirms;

    /** 注入目录与三个用例。 */
    public ClientClassificationAdminController(CatalogItemRepository catalog, GetClientClassificationProcessor reads,
        ReplaceClientClassificationProcessor replaces, ConfirmClientClassificationProcessor confirms) {
        this.catalog = catalog;
        this.reads = reads;
        this.replaces = replaces;
        this.confirms = confirms;
    }

    /** 行业目录：通用目录 INDUSTRY 的全局 ACTIVE 项，只返回代码与文案键。 */
    @Override
    public ClassificationOptionsResponseBody options(String actorAccountId, String sessionId) {
        actor(actorAccountId, sessionId);
        return new ClassificationOptionsResponseBody(catalog.findActive(List.of("INDUSTRY"), OnboardingPack.ANY).stream()
            .map(industry -> new CatalogOptionResponseBody(industry.getItemCode(), industry.getLabelKey())).toList());
    }

    /** 当前分类。 */
    @Override
    public ClassificationSetResponseBody get(String actorAccountId, String sessionId, String caseId) {
        return body(reads.process(new ClientClassificationRequest(id(caseId), actor(actorAccountId, sessionId), List.of())));
    }

    /** O03 整体替换。 */
    @Override
    public ClassificationSetResponseBody replace(String actorAccountId, String sessionId, String caseId,
        ClassificationSetRequestBody request) {
        var items = request.getItems().stream()
            .map(item -> new ClientClassificationRequest.Item(item.getPrimary(), item.getMccCode(), item.getIndustryCode())).toList();
        return body(replaces.process(new ClientClassificationRequest(id(caseId), actor(actorAccountId, sessionId), items)));
    }

    /** O04 确认。 */
    @Override
    public ClassificationSetResponseBody confirm(String actorAccountId, String sessionId, String caseId) {
        return body(confirms.process(new ClientClassificationRequest(id(caseId), actor(actorAccountId, sessionId), List.of())));
    }

    /** 可信操作者；会话只要求存在，权限由 Gateway/BFF 负责。 */
    static long actor(String actorAccountId, String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 128) {
            throw new InvalidParameterException("操作者会话无效");
        }
        try {
            return id(actorAccountId);
        } catch (FailException invalid) {
            throw new InvalidParameterException("操作者无效");
        }
    }

    /** 正 BIGINT 路径参数；非法按任务不存在处理。 */
    static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new FailException(REVIEW_CASE_NOT_FOUND);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new FailException(REVIEW_CASE_NOT_FOUND);
        }
    }

    /** Wire 响应；ID 与时间为字符串。 */
    static ClassificationSetResponseBody body(ClientClassificationResponse response) {
        var profile = response.getProfile();
        boolean confirmed = response.confirmed();
        return new ClassificationSetResponseBody(Long.toString(response.getCaseId()), Long.toString(response.getApplicationId()), confirmed,
            confirmed ? Long.toString(profile.getClassificationConfirmedBy()) : null,
            confirmed ? new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(profile.getClassificationConfirmedTime()) : null,
            response.getItems().stream()
                .map(row -> new ClassificationItemResponseBody(row.getIndustryCode(), row.getMccCode(), row.isPrimary())).toList());
    }
}
