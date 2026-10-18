package com.paytech.platform.support.delivery.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.delivery.core.configuration.NotificationProperties;
import com.paytech.platform.support.delivery.core.constant.NotificationProviderTypes;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplateContent;
import com.paytech.platform.support.delivery.core.enums.NotificationAuditField;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;
import com.paytech.platform.support.delivery.domain.ChangeNotificationStatusCommand;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;
import com.paytech.platform.support.delivery.domain.NotificationContentItem;
import com.paytech.platform.support.delivery.domain.NotificationFieldChange;
import com.paytech.platform.support.delivery.domain.NotificationInput;
import com.paytech.platform.support.delivery.domain.NotificationPreview;
import com.paytech.platform.support.delivery.domain.NotificationProviderReference;
import com.paytech.platform.support.delivery.domain.NotificationProviderView;
import com.paytech.platform.support.delivery.domain.NotificationRouteItem;
import com.paytech.platform.support.delivery.domain.NotificationTemplateDetailView;
import com.paytech.platform.support.delivery.domain.NotificationTemplateIssue;
import com.paytech.platform.support.delivery.domain.NotificationTemplateSummary;
import com.paytech.platform.support.delivery.domain.ReplaceNotificationCredentialCommand;
import com.paytech.platform.support.delivery.domain.SaveNotificationContentCommand;
import com.paytech.platform.support.delivery.domain.SaveNotificationProviderCommand;
import com.paytech.platform.support.delivery.domain.SaveNotificationRouteCommand;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationProviderRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationRouteRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateContentRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateRepository;
import com.paytech.platform.support.delivery.integration.provider.EmailProviders;
import com.paytech.platform.support.delivery.service.NotificationManagementService;

import lombok.RequiredArgsConstructor;

/**
 * 运营维护通知配置：只读写本库，写方法加入调用方事务
 * <li>随 Operations 管理入口启用，不依赖 notification.enabled；未开启发送时适配器视为未配置</li>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class NotificationManagementServiceImpl implements NotificationManagementService {

    /** 首次录入密钥的审计值。 */
    static final String CREDENTIAL_SET = "SET";

    /** 更换已有密钥的审计值。 */
    static final String CREDENTIAL_REPLACED = "REPLACED";

    private final IdGenerator ids;
    private final NotificationProperties properties;
    private final NotificationRouteRepository routes;
    private final ObjectProvider<EmailProviders> adapters;
    private final NotificationTemplateRepository templates;
    private final NotificationProviderRepository providers;
    private final NotificationTemplateContentRepository contents;

    /** 一页模板及其语言、路由状态，三表各查一次。 */
    @Override
    public PagingResponseBody<NotificationTemplateSummary> templates(String keyword,
        NotificationConfigurationStatus status, PagingRequestBody paging) {
        var rows = templates.page(keyword, status, paging.offset(), paging.size());
        var templateIds = rows.stream().map(NotificationTemplate::getTemplateId).toList();
        Map<Long, List<String>> locales = contents.findTemplates(templateIds).stream()
            .collect(Collectors.groupingBy(NotificationTemplateContent::getTemplateId,
                Collectors.mapping(NotificationTemplateContent::getLocale, Collectors.toList())));
        Map<Long, NotificationConfigurationStatus> routeStatus = routes.findTemplates(templateIds).stream()
            .collect(Collectors.toMap(NotificationRoute::getNotificationTemplateId, NotificationRoute::getStatus));
        var items = rows.stream().map(t -> new NotificationTemplateSummary(t.getTemplateCode(), t.getMedium(),
            t.getStatus(), locales.getOrDefault(t.getTemplateId(), List.of()),
            properties.isTransientTemplate(t.getTemplateCode()), routeStatus.get(t.getTemplateId()), t.getVersion(),
            t.getUpdateTime())).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), templates.count(keyword, status));
    }

    /** 模板、全部语言内容与路由；路由账号转为编码。 */
    @Override
    public NotificationTemplateDetailView template(String templateCode) {
        NotificationTemplate template = requireTemplate(templateCode);
        var items = contents.findTemplates(List.of(template.getTemplateId())).stream()
            .map(c -> new NotificationContentItem(c.getLocale(), c.getSubjectTemplate(), c.getBodyTemplate(),
                c.getVersion(), c.getUpdateTime()))
            .toList();
        return new NotificationTemplateDetailView(template.getTemplateCode(), template.getMedium(),
            template.getStatus(), properties.isTransientTemplate(templateCode), requiredParameters(templateCode),
            template.getVersion(), template.getUpdateTime(), items, routeItem(template));
    }

    /** 新建停用状态的 EMAIL 模板；编码已存在时拒绝。 */
    @Override
    public NotificationConfigurationChange createTemplate(String templateCode, Instant now) {
        if (templates.findCode(templateCode) != null) {
            throw new BizCheckFailException("模板编码已存在: " + templateCode);
        }
        var template = new NotificationTemplate();
        template.setTemplateId(ids.nextId());
        template.setTemplateCode(templateCode);
        template.setMedium(NotificationMedium.EMAIL);
        template.setStatus(NotificationConfigurationStatus.DISABLED);
        template.setVersion(1);
        template.setCreateTime(now);
        template.setUpdateTime(now);
        templates.insert(template);
        return new NotificationConfigurationChange(templateCode, 0, 1,
            List.of(change(NotificationAuditField.CODE, null, templateCode),
                change(NotificationAuditField.STATUS, null, template.getStatus().getCode())));
    }

    /** 启停模板；启用时至少要有一条语言内容。 */
    @Override
    public NotificationConfigurationChange changeTemplateStatus(ChangeNotificationStatusCommand command) {
        NotificationTemplate template = requireTemplate(command.getCode());
        requireVersion(template.getVersion(), command.getExpectedVersion());
        requireStatusChange(template.getStatus(), command.getStatus());
        if (command.getStatus() == NotificationConfigurationStatus.ACTIVE
            && contents.countTemplate(template.getTemplateId()) == 0) {
            throw new BizCheckFailException("模板没有语言内容，不能启用: " + command.getCode());
        }
        int beforeVersion = template.getVersion();
        String before = template.getStatus().getCode();
        templates.changeStatus(template, command.getStatus(), command.getNow());
        return new NotificationConfigurationChange(command.getCode(), beforeVersion, template.getVersion(),
            List.of(change(NotificationAuditField.STATUS, before, command.getStatus().getCode())));
    }

    /** 按发送规则检查语言内容；即时发送模板的正文必须包含链接参数。 */
    @Override
    public List<NotificationTemplateIssue> contentIssues(String templateCode, String subject, String body) {
        requireTemplate(templateCode);
        return NotificationInput.contentIssues(subject, body, requiredParameters(templateCode));
    }

    /** 新建（expectedVersion=0）或按版本修改语言内容；审计只记标题与正文摘要。 */
    @Override
    public NotificationConfigurationChange saveContent(SaveNotificationContentCommand command) {
        NotificationTemplate template = requireTemplate(command.getTemplateCode());
        var existing = contents.findLocale(template.getTemplateId(), command.getLocale());
        if (command.getExpectedVersion() == 0) {
            return createContent(template, existing, command);
        }
        if (existing == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        requireVersion(existing.getVersion(), command.getExpectedVersion());
        int beforeVersion = existing.getVersion();
        var changes = contentChanges(existing.getSubjectTemplate(), existing.getBodyTemplate(), command);
        contents.update(existing, command.getSubjectTemplate(), command.getBodyTemplate(), command.getNow());
        return new NotificationConfigurationChange(command.getLocale(), beforeVersion, existing.getVersion(), changes);
    }

    /** 新建语言内容；该语言已存在说明页面数据过期。 */
    NotificationConfigurationChange createContent(NotificationTemplate template, NotificationTemplateContent existing,
        SaveNotificationContentCommand command) {
        if (existing != null) {
            throw new ConcurrentRequestException("该语言内容已存在，请刷新后重试");
        }
        var content = new NotificationTemplateContent();
        content.setContentId(ids.nextId());
        content.setTemplateId(template.getTemplateId());
        content.setLocale(command.getLocale());
        content.setSubjectTemplate(command.getSubjectTemplate());
        content.setBodyTemplate(command.getBodyTemplate());
        content.setVersion(1);
        content.setCreateTime(command.getNow());
        content.setUpdateTime(command.getNow());
        contents.insert(content);
        return new NotificationConfigurationChange(command.getLocale(), 0, 1,
            contentChanges(null, null, command));
    }

    /** 语言及标题、正文摘要的前后值。 */
    List<NotificationFieldChange> contentChanges(String subject, String body, SaveNotificationContentCommand command) {
        return List.of(change(NotificationAuditField.LOCALE, subject == null ? null : command.getLocale(),
            command.getLocale()),
            change(NotificationAuditField.SUBJECT_DIGEST, digest(subject), digest(command.getSubjectTemplate())),
            change(NotificationAuditField.BODY_DIGEST, digest(body), digest(command.getBodyTemplate())));
    }

    /** 渲染草稿，缺少的参数保留原占位符；不读库、不发送。 */
    @Override
    public NotificationPreview preview(String subject, String body, Map<String, String> parameters) {
        return NotificationInput.preview(subject, body, parameters);
    }

    /** 新建（expectedVersion=0，状态启用）或按版本替换主备账号。 */
    @Override
    public NotificationConfigurationChange saveRoute(SaveNotificationRouteCommand command) {
        NotificationTemplate template = requireTemplate(command.getTemplateCode());
        NotificationProvider primary = requireRoutableProvider(command.getPrimaryProviderCode());
        NotificationProvider fallback = command.getFallbackProviderCode() == null ? null
            : requireRoutableProvider(command.getFallbackProviderCode());
        if (fallback != null && fallback.getProviderId().equals(primary.getProviderId())) {
            throw new BizCheckFailException("备用账号不能与主账号相同");
        }
        var existing = routes.findTemplate(template.getTemplateId());
        if (command.getExpectedVersion() == 0) {
            return createRoute(template, existing, primary, fallback, command);
        }
        if (existing == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return changeRoute(existing, primary, fallback, command);
    }

    /** 按版本替换主备账号，冻结替换前的账号编码供审计。 */
    NotificationConfigurationChange changeRoute(NotificationRoute route, NotificationProvider primary,
        NotificationProvider fallback, SaveNotificationRouteCommand command) {
        requireVersion(route.getVersion(), command.getExpectedVersion());
        int beforeVersion = route.getVersion();
        var codes = providerCodes(route);
        String beforePrimary = codes.get(route.getPrimaryProviderId());
        String beforeFallback = route.getFallbackProviderId() == null ? null : codes.get(route.getFallbackProviderId());
        routes.changeProviders(route, primary.getProviderId(), fallback == null ? null : fallback.getProviderId(),
            command.getNow());
        return new NotificationConfigurationChange(command.getTemplateCode(), beforeVersion, route.getVersion(),
            List.of(change(NotificationAuditField.PRIMARY_PROVIDER, beforePrimary, primary.getProviderCode()),
                change(NotificationAuditField.FALLBACK_PROVIDER, beforeFallback, command.getFallbackProviderCode())));
    }

    /** 新建启用状态的路由；模板已有路由说明页面数据过期。 */
    NotificationConfigurationChange createRoute(NotificationTemplate template, NotificationRoute existing,
        NotificationProvider primary, NotificationProvider fallback, SaveNotificationRouteCommand command) {
        if (existing != null) {
            throw new ConcurrentRequestException("该模板已有路由，请刷新后重试");
        }
        var route = new NotificationRoute();
        route.setRouteId(ids.nextId());
        route.setNotificationTemplateId(template.getTemplateId());
        route.setPrimaryProviderId(primary.getProviderId());
        route.setFallbackProviderId(fallback == null ? null : fallback.getProviderId());
        route.setStatus(NotificationConfigurationStatus.ACTIVE);
        route.setVersion(1);
        route.setCreateTime(command.getNow());
        route.setUpdateTime(command.getNow());
        routes.insert(route);
        return new NotificationConfigurationChange(command.getTemplateCode(), 0, 1,
            List.of(change(NotificationAuditField.PRIMARY_PROVIDER, null, primary.getProviderCode()),
                change(NotificationAuditField.FALLBACK_PROVIDER, null, command.getFallbackProviderCode()),
                change(NotificationAuditField.STATUS, null, route.getStatus().getCode())));
    }

    /** 启停路由；启用时主备账号仍须可用。 */
    @Override
    public NotificationConfigurationChange changeRouteStatus(ChangeNotificationStatusCommand command) {
        NotificationTemplate template = requireTemplate(command.getCode());
        NotificationRoute route = routes.findTemplate(template.getTemplateId());
        if (route == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        requireVersion(route.getVersion(), command.getExpectedVersion());
        requireStatusChange(route.getStatus(), command.getStatus());
        if (command.getStatus() == NotificationConfigurationStatus.ACTIVE) {
            providerCodes(route).values().forEach(this::requireRoutableProvider);
        }
        int beforeVersion = route.getVersion();
        String before = route.getStatus().getCode();
        routes.changeStatus(route, command.getStatus(), command.getNow());
        return new NotificationConfigurationChange(command.getCode(), beforeVersion, route.getVersion(),
            List.of(change(NotificationAuditField.STATUS, before, command.getStatus().getCode())));
    }

    /** 一页账号及其路由引用；适配器配置情况来自本进程注册的发送适配器。 */
    @Override
    public PagingResponseBody<NotificationProviderView> providers(PagingRequestBody paging) {
        var rows = providers.page(paging.offset(), paging.size());
        var providerIds = rows.stream().map(NotificationProvider::getProviderId).toList();
        var routeRows = routes.findProviders(providerIds);
        Map<Long, String> templateCodes = templates.findIds(routeRows.stream()
            .map(NotificationRoute::getNotificationTemplateId).distinct().toList()).stream()
            .collect(Collectors.toMap(NotificationTemplate::getTemplateId, NotificationTemplate::getTemplateCode));
        EmailProviders registry = adapters.getIfAvailable();
        var items = rows.stream().map(p -> new NotificationProviderView(p.getProviderCode(), p.getMedium(),
            p.getProviderType(), p.getStatus(), p.getRegion(), p.getFromAddress(), p.getConfigurationSet(),
            p.getCredentialVaultToken() != null, p.getCredentialUpdateTime(), p.missingConfiguration(),
            registry != null && registry.isRegistered(p.getProviderType()), p.getEventQueueUrl(), p.getEventPollTime(),
            p.getEventPollError(), p.getEventReceiveTime(), p.getVersion(), p.getUpdateTime(),
            references(p.getProviderId(), routeRows, templateCodes))).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), providers.count());
    }

    /** 引用某账号的路由，主账号与备用账号分别标记。 */
    List<NotificationProviderReference> references(Long providerId, List<NotificationRoute> routeRows,
        Map<Long, String> templateCodes) {
        var references = new ArrayList<NotificationProviderReference>();
        for (NotificationRoute route : routeRows) {
            String templateCode = templateCodes.get(route.getNotificationTemplateId());
            if (providerId.equals(route.getPrimaryProviderId())) {
                references.add(new NotificationProviderReference(templateCode, "PRIMARY", route.getStatus()));
            }
            if (providerId.equals(route.getFallbackProviderId())) {
                references.add(new NotificationProviderReference(templateCode, "FALLBACK", route.getStatus()));
            }
        }
        return references;
    }

    /** 启停发送账号；启用时要求配置完整，不检查引用，由调用方提示影响。 */
    @Override
    public NotificationConfigurationChange changeProviderStatus(ChangeNotificationStatusCommand command) {
        NotificationProvider provider = requireProvider(command.getCode());
        requireVersion(provider.getVersion(), command.getExpectedVersion());
        requireStatusChange(provider.getStatus(), command.getStatus());
        var missing = provider.missingConfiguration();
        if (command.getStatus() == NotificationConfigurationStatus.ACTIVE && !missing.isEmpty()) {
            throw new BizCheckFailException("账号配置不完整，缺少: " + String.join("、", missing));
        }
        int beforeVersion = provider.getVersion();
        String before = provider.getStatus().getCode();
        providers.changeStatus(provider, command.getStatus(), command.getNow());
        return new NotificationConfigurationChange(command.getCode(), beforeVersion, provider.getVersion(),
            List.of(change(NotificationAuditField.STATUS, before, command.getStatus().getCode())));
    }

    /** 新建停用状态的发送账号；介质由类型决定，本期类型都是 EMAIL。 */
    @Override
    public NotificationConfigurationChange createProvider(SaveNotificationProviderCommand command) {
        if (!NotificationProviderTypes.CREATABLE.contains(command.getProviderType())) {
            throw new BizCheckFailException("不支持的服务商类型: " + command.getProviderType());
        }
        if (providers.findCode(command.getProviderCode()) != null) {
            throw new BizCheckFailException("账号编码已存在: " + command.getProviderCode());
        }
        var provider = new NotificationProvider();
        provider.setProviderId(ids.nextId());
        provider.setProviderCode(command.getProviderCode());
        provider.setMedium(NotificationMedium.EMAIL);
        provider.setProviderType(command.getProviderType());
        provider.setStatus(NotificationConfigurationStatus.DISABLED);
        provider.setRegion(command.getRegion());
        provider.setFromAddress(NotificationInput.canonicalEmail(command.getFromAddress()));
        provider.setConfigurationSet(blankToNull(command.getConfigurationSet()));
        provider.setEventQueueUrl(blankToNull(command.getEventQueueUrl()));
        provider.setVersion(1);
        provider.setCreateTime(command.getNow());
        provider.setUpdateTime(command.getNow());
        providers.insert(provider);
        return new NotificationConfigurationChange(command.getProviderCode(), 0, 1,
            List.of(change(NotificationAuditField.CODE, null, provider.getProviderCode()),
                change(NotificationAuditField.PROVIDER_TYPE, null, provider.getProviderType()),
                change(NotificationAuditField.REGION, null, provider.getRegion()),
                change(NotificationAuditField.FROM_ADDRESS, null, provider.getFromAddress()),
                change(NotificationAuditField.CONFIGURATION_SET, null, provider.getConfigurationSet()),
                change(NotificationAuditField.EVENT_QUEUE_URL, null, provider.getEventQueueUrl()),
                change(NotificationAuditField.STATUS, null, provider.getStatus().getCode())));
    }

    /** 按版本修改账号的区域、发件地址、Configuration Set 与事件队列；审计记录前后值。 */
    @Override
    public NotificationConfigurationChange updateProvider(SaveNotificationProviderCommand command) {
        NotificationProvider provider = requireProvider(command.getProviderCode());
        requireVersion(provider.getVersion(), command.getExpectedVersion());
        int beforeVersion = provider.getVersion();
        String fromAddress = NotificationInput.canonicalEmail(command.getFromAddress());
        String configurationSet = blankToNull(command.getConfigurationSet());
        String eventQueueUrl = blankToNull(command.getEventQueueUrl());
        var changes = List.of(change(NotificationAuditField.REGION, provider.getRegion(), command.getRegion()),
            change(NotificationAuditField.FROM_ADDRESS, provider.getFromAddress(), fromAddress),
            change(NotificationAuditField.CONFIGURATION_SET, provider.getConfigurationSet(), configurationSet),
            change(NotificationAuditField.EVENT_QUEUE_URL, provider.getEventQueueUrl(), eventQueueUrl));
        providers.updateConfiguration(provider, command.getRegion(), fromAddress, configurationSet, eventQueueUrl,
            command.getNow());
        return new NotificationConfigurationChange(command.getProviderCode(), beforeVersion, provider.getVersion(),
            changes);
    }

    /** 更换密钥前检查账号存在且版本一致。 */
    @Override
    public long requireCredentialReplaceable(String providerCode, int expectedVersion) {
        NotificationProvider provider = requireProvider(providerCode);
        requireVersion(provider.getVersion(), expectedVersion);
        return provider.getProviderId();
    }

    /** 以新 Token 替换密钥；版本在 Vault 写入期间变化时返回并发冲突，新密文不再被引用。 */
    @Override
    public NotificationConfigurationChange replaceCredential(ReplaceNotificationCredentialCommand command) {
        NotificationProvider provider = requireProvider(command.getProviderCode());
        requireVersion(provider.getVersion(), command.getExpectedVersion());
        int beforeVersion = provider.getVersion();
        String before = provider.getCredentialVaultToken() == null ? null : CREDENTIAL_SET;
        String after = before == null ? CREDENTIAL_SET : CREDENTIAL_REPLACED;
        providers.replaceCredential(provider, command.getCredentialToken(), command.getNow());
        return new NotificationConfigurationChange(command.getProviderCode(), beforeVersion, provider.getVersion(),
            List.of(change(NotificationAuditField.CREDENTIAL, before, after)));
    }

    /** 测试发送只追加审计，账号版本不变。 */
    @Override
    public NotificationConfigurationChange testSendRecord(String providerCode, String recipient, String resultCode) {
        NotificationProvider provider = requireProvider(providerCode);
        return new NotificationConfigurationChange(providerCode, provider.getVersion(), provider.getVersion(),
            List.of(change(NotificationAuditField.TEST_RECIPIENT_DIGEST, null, digest(recipient)),
                change(NotificationAuditField.TEST_RESULT, null, resultCode)));
    }

    /** 账号不存在返回 NOT_FOUND。 */
    NotificationProvider requireProvider(String providerCode) {
        NotificationProvider provider = providers.findCode(providerCode);
        if (provider == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return provider;
    }

    /** 空白视为未填写。 */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 模板不存在返回 NOT_FOUND。 */
    NotificationTemplate requireTemplate(String templateCode) {
        NotificationTemplate template = templates.findCode(templateCode);
        if (template == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return template;
    }

    /** 路由账号必须存在、属于 EMAIL 且启用。 */
    NotificationProvider requireRoutableProvider(String providerCode) {
        NotificationProvider provider = providers.findCode(providerCode);
        if (provider == null || provider.getMedium() != NotificationMedium.EMAIL
            || provider.getStatus() != NotificationConfigurationStatus.ACTIVE) {
            throw new BizCheckFailException("发送账号不存在或未启用: " + providerCode);
        }
        return provider;
    }

    /** 即时发送模板要求正文包含链接参数，普通模板没有必需参数。 */
    List<String> requiredParameters(String templateCode) {
        if (properties.isTransientTemplate(templateCode)) {
            return List.of(NotificationInput.TRANSIENT_LINK_PARAMETER);
        }
        return List.of();
    }

    /** 模板路由的账号编码视图；未配置路由时为空。 */
    NotificationRouteItem routeItem(NotificationTemplate template) {
        NotificationRoute route = routes.findTemplate(template.getTemplateId());
        if (route == null) {
            return null;
        }
        var codes = providerCodes(route);
        return new NotificationRouteItem(codes.get(route.getPrimaryProviderId()),
            route.getFallbackProviderId() == null ? null : codes.get(route.getFallbackProviderId()),
            route.getStatus(), route.getVersion(), route.getUpdateTime());
    }

    /** 路由主备账号身份到编码。 */
    Map<Long, String> providerCodes(NotificationRoute route) {
        var providerIds = new ArrayList<Long>();
        providerIds.add(route.getPrimaryProviderId());
        if (route.getFallbackProviderId() != null) {
            providerIds.add(route.getFallbackProviderId());
        }
        return providers.findIds(providerIds).stream()
            .collect(Collectors.toMap(NotificationProvider::getProviderId, NotificationProvider::getProviderCode));
    }

    /** 调用方看到的版本必须是当前版本。 */
    void requireVersion(int actual, int expected) {
        if (actual != expected) {
            throw new ConcurrentRequestException("配置已被其他操作修改，请刷新后重试");
        }
    }

    /** 目标状态与当前相同时拒绝。 */
    void requireStatusChange(NotificationConfigurationStatus current, NotificationConfigurationStatus target) {
        if (current == target) {
            throw new BizCheckFailException("状态未变化: " + target.getCode());
        }
    }

    /** 单个字段差异。 */
    NotificationFieldChange change(NotificationAuditField field, String before, String after) {
        return new NotificationFieldChange(field, before, after);
    }

    /** 文案的 SHA-256 十六进制摘要；空值返回空。 */
    static String digest(String text) {
        if (text == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new ErrorException("SHA-256 不可用", unavailable);
        }
    }
}
