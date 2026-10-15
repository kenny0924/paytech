package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 通知模板、语言内容、路由与发送账号的维护，以及普通通知发送记录查询
 * <li>沿用 BFF 配置管理的 record 模型，供严格 Body 解析器逐字段检查</li>
 * <li>不含收件邮箱、Vault Token、参数值、供应商流水号、发件地址与凭据</li>
 */
public final class NotificationManagementModels {
    /** 只承载类型，禁止创建实例。 */
    private NotificationManagementModels() {}

    /** 模板列表项。
     * @param templateCode 模板编码。
     * @param medium 介质，本期为 EMAIL。
     * @param status 模板状态：ACTIVE、DISABLED。
     * @param locales 已有语言，升序。
     * @param transientTemplate 是否只能即时发送（认证邮件）。
     * @param routeStatus 路由状态，未配置时为 null。
     * @param version 模板版本。
     * @param updateTime 模板更新时间，UTC。
     */
    public record NotificationTemplateSummary(String templateCode, String medium, String status, List<String> locales,
        boolean transientTemplate, String routeStatus, int version, Instant updateTime) {}

    /** 模板某一语言的内容。
     * @param locale 语言。
     * @param subjectTemplate 标题模板。
     * @param bodyTemplate 正文模板。
     * @param version 内容版本。
     * @param updateTime 更新时间，UTC。
     */
    public record TemplateContentItem(String locale, String subjectTemplate, String bodyTemplate, int version,
        Instant updateTime) {}

    /** 模板路由。
     * @param primaryProviderCode 主账号编码。
     * @param fallbackProviderCode 备用账号编码，可空。
     * @param status 路由状态。
     * @param version 路由版本。
     * @param updateTime 更新时间，UTC。
     */
    public record TemplateRouteItem(String primaryProviderCode, String fallbackProviderCode, String status,
        int version, Instant updateTime) {}

    /** 模板详情。
     * @param templateCode 模板编码。
     * @param medium 介质。
     * @param status 模板状态。
     * @param transientTemplate 是否只能即时发送（认证邮件）。
     * @param requiredParameters 正文必须包含的占位符名称。
     * @param version 模板版本。
     * @param updateTime 模板更新时间，UTC。
     * @param contents 全部语言内容，按语言升序。
     * @param route 路由，未配置时为 null。
     */
    public record NotificationTemplateDetailResponseBody(String templateCode, String medium, String status,
        boolean transientTemplate, List<String> requiredParameters, int version, Instant updateTime,
        List<TemplateContentItem> contents, TemplateRouteItem route) {}

    /** 写入结果。
     * @param code 对象编码：模板编码、语言或账号编码。
     * @param version 写入后的版本。
     */
    public record NotificationMutationResponseBody(String code, int version) {}

    /** 预览结果。
     * @param subject 渲染后的标题。
     * @param body 渲染后的正文。
     * @param missingParameters 未提供值的占位符，在结果中原样保留。
     */
    public record NotificationPreviewResponseBody(String subject, String body, List<String> missingParameters) {}

    /** 引用发送账号的路由。
     * @param templateCode 路由所属模板编码。
     * @param role PRIMARY 或 FALLBACK。
     * @param routeStatus 路由状态。
     */
    public record ProviderReferenceItem(String templateCode, String role, String routeStatus) {}

    /** 发送账号列表项。
     * @param providerCode 账号编码。
     * @param medium 介质。
     * @param status 账号状态。
     * @param adapterConfigured 本环境是否已配置发送适配器。
     * @param version 账号版本。
     * @param updateTime 更新时间，UTC。
     * @param references 引用该账号的路由。
     */
    public record NotificationProviderSummary(String providerCode, String medium, String status,
        boolean adapterConfigured, int version, Instant updateTime, List<ProviderReferenceItem> references) {}

    /** 发送记录列表项。
     * @param requestId 请求 ID。
     * @param sourceSystem 来源系统。
     * @param sourceEventKey 来源事件键。
     * @param templateCode 模板编码。
     * @param tenantId 租户 ID，平台通知为 null。
     * @param defaultLocale 默认语言。
     * @param deliveryCount 收件人数。
     * @param statusCounts 各投递状态的数量。
     * @param createTime 受理时间，UTC。
     */
    public record NotificationRequestSummary(String requestId, String sourceSystem, String sourceEventKey,
        String templateCode, String tenantId, String defaultLocale, int deliveryCount,
        Map<String, Integer> statusCounts, Instant createTime) {}

    /** 一次发送尝试。
     * @param attemptId 尝试 ID。
     * @param providerCode 实际使用的账号编码。
     * @param status 尝试状态。
     * @param responseCode 发送响应码，可空。
     * @param resolutionSource 结果确认来源，可空。
     * @param resolutionCode 确认结果码，可空。
     * @param startedTime 开始时间。
     * @param finishedTime 发送调用结束时间，可空。
     * @param resolvedTime 结果确认时间，可空。
     */
    public record AttemptItem(String attemptId, String providerCode, String status, String responseCode,
        String resolutionSource, String resolutionCode, Instant startedTime, Instant finishedTime,
        Instant resolvedTime) {}

    /** 一个收件人的投递。
     * @param deliveryId 投递 ID。
     * @param locale 收件人语言，null 表示继承默认语言。
     * @param parameterKeys 收件人覆盖参数的名称。
     * @param status 投递状态。
     * @param attemptCount 已创建尝试数。
     * @param nextRetryTime 下次重试时间，可空。
     * @param updateTime 更新时间，UTC。
     * @param attempts 发送尝试，按先后。
     */
    public record DeliveryItem(String deliveryId, String locale, List<String> parameterKeys, String status,
        int attemptCount, Instant nextRetryTime, Instant updateTime, List<AttemptItem> attempts) {}

    /** 发送记录详情。
     * @param requestId 请求 ID。
     * @param sourceSystem 来源系统。
     * @param sourceEventKey 来源事件键。
     * @param templateCode 模板编码。
     * @param tenantId 租户 ID。
     * @param defaultLocale 默认语言。
     * @param parameterKeys 默认参数名称，不含值。
     * @param routeId 受理时选择的路由 ID。
     * @param createTime 受理时间，UTC。
     * @param deliveries 投递，按先后。
     */
    public record NotificationRequestDetailResponseBody(String requestId, String sourceSystem, String sourceEventKey,
        String templateCode, String tenantId, String defaultLocale, List<String> parameterKeys, String routeId,
        Instant createTime, List<DeliveryItem> deliveries) {}

    /** 新建停用状态的 EMAIL 模板。
     * @param templateCode 模板编码，创建后不可修改。
     * @param reason 操作原因。
     */
    public record CreateNotificationTemplateRequestBody(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String templateCode,
        @NotBlank @Size(max = 500) String reason) {}

    /** 启停模板、路由或发送账号。
     * @param status 目标状态 ACTIVE 或 DISABLED。
     * @param expectedVersion 当前版本。
     * @param reason 操作原因。
     */
    public record ChangeNotificationStatusRequestBody(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @Positive int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {}

    /** 新建或修改某一语言的内容。
     * @param subjectTemplate 标题模板，最多 1024 字符。
     * @param bodyTemplate 正文模板，最多 60000 字符。
     * @param expectedVersion 当前版本，0 为新建。
     * @param reason 操作原因。
     */
    public record SaveNotificationContentRequestBody(
        @NotNull @Size(max = 1024) String subjectTemplate,
        @NotNull @Size(max = 60000) String bodyTemplate,
        @PositiveOrZero int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {}

    /** 预览内容草稿。
     * @param subjectTemplate 标题模板草稿。
     * @param bodyTemplate 正文模板草稿。
     * @param parameters 示例参数，可空，最多 64 项。
     */
    public record PreviewNotificationContentRequestBody(
        @NotNull @Size(max = 1024) String subjectTemplate,
        @NotNull @Size(max = 60000) String bodyTemplate,
        @Size(max = 64) Map<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String,
            @NotNull @Size(max = 2048) String> parameters) {}

    /** 新建或修改路由。
     * @param primaryProviderCode 主账号编码。
     * @param fallbackProviderCode 备用账号编码，可空。
     * @param expectedVersion 当前版本，0 为新建。
     * @param reason 操作原因。
     */
    public record SaveNotificationRouteRequestBody(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String primaryProviderCode,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String fallbackProviderCode,
        @PositiveOrZero int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {}
}
