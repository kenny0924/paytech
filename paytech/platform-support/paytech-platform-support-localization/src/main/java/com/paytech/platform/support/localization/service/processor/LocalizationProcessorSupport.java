package com.paytech.platform.support.localization.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domain.enums.AuditActorType;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.localization.core.configuration.LocalizationProperties;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.domain.LocalizationJson;
import com.paytech.platform.support.localization.domain.release.CurrentTexts;
import com.paytech.platform.support.localization.domain.release.ReleaseAssessment;
import com.paytech.platform.support.localization.domain.release.ReleaseAssessor;
import com.paytech.platform.support.localization.domainservice.repository.I18nApplicationRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nLocaleRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nTranslationRepository;

/**
 * 多语言各入口共用的本地能力：权限重检与审计身份、本地写事务、资源锁参数、应用与语言查找、发布评估输入。不访问外部服务。
 * <li>权限：I18N_VIEW 查看，I18N_EDIT 维护语言、应用、命名空间、导入与翻译，I18N_PUBLISH 发布与切换（技术方案 3.9）。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class LocalizationProcessorSupport {

    /** 查看。 */
    public static final String VIEW_PERMISSION = "I18N_VIEW";

    /** 维护语言、应用、命名空间、导入与翻译。 */
    public static final String EDIT_PERMISSION = "I18N_EDIT";

    /** 发布与切换当前发布。 */
    public static final String PUBLISH_PERMISSION = "I18N_PUBLISH";

    /** 语言的审计资源类型。 */
    public static final String LOCALE_AUDIT_TYPE = "I18N_LOCALE";

    /** 应用的审计资源类型；key 导入与翻译写入也以应用为资源。 */
    public static final String APPLICATION_AUDIT_TYPE = "I18N_APPLICATION";

    /** 命名空间的审计资源类型。 */
    public static final String NAMESPACE_AUDIT_TYPE = "I18N_NAMESPACE";

    /** 发布与切换的审计资源类型。 */
    public static final String RELEASE_AUDIT_TYPE = "I18N_RELEASE";

    private final Clock clock;
    private final IdGenerator ids;
    private final I18nKeyRepository keys;
    private final I18nLocaleRepository locales;
    private final OperationsAuditService audit;
    private final TransactionTemplate transaction;
    private final LocalizationProperties properties;
    private final I18nApplicationRepository applications;
    private final I18nTranslationRepository translations;
    private final OperationsManagementPolicyService policy;

    /**
     * 装配依赖并固定写事务：读已提交、15 秒超时，与 Operations 其他写入口一致。
     *
     * @param clock 时钟
     * @param ids ID 生成器
     * @param keys key 仓储
     * @param locales 语言仓储
     * @param audit Operations 审计
     * @param manager 本库事务管理器
     * @param properties 多语言配置
     * @param applications 应用仓储
     * @param translations 翻译仓储
     * @param policy 权限重检
     */
    public LocalizationProcessorSupport(Clock clock, IdGenerator ids, I18nKeyRepository keys,
        I18nLocaleRepository locales, OperationsAuditService audit,
        @Qualifier("transactionManager") PlatformTransactionManager manager, LocalizationProperties properties,
        I18nApplicationRepository applications, I18nTranslationRepository translations,
        OperationsManagementPolicyService policy) {
        this.clock = clock;
        this.ids = ids;
        this.keys = keys;
        this.locales = locales;
        this.audit = audit;
        this.properties = properties;
        this.applications = applications;
        this.translations = translations;
        this.policy = policy;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transaction.setTimeout(15);
    }

    /**
     * 重检会话持有权限，并登记审计身份。
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param permission 所需权限
     * @return 当前会话
     */
    public SessionContext authorize(String actor, String session, String permission) {
        SessionContext context = policy.requirePermission(actor, session, permission);
        audit.identify(() -> new Identity(actor, context.member().tenantId(), AuditActorType.USER));
        return context;
    }

    /**
     * 在本库写事务中执行：先复核权限，事务开始后登记审计完成通知；事务内不访问外部能力。
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param permission 所需权限
     * @param operation 本地写入
     * @param <T> 结果类型
     * @return 结果
     */
    public <T> T write(String actor, String session, String permission, Supplier<T> operation) {
        return transaction.execute(status -> {
            audit.observeTransaction();
            policy.requirePermission(actor, session, permission);
            return operation.get();
        });
    }

    /**
     * 登记本次成功写入的审计事实；资源类型须属于入口 {@code @Audit} 声明的类型，事务提交后才会落审计。
     *
     * @param context 当前会话
     * @param targetType 审计资源类型，I18N_*
     * @param targetId 资源 ID
     * @param reason 有界说明，不含文案正文
     */
    public void recordSucceeded(SessionContext context, String targetType, long targetId, String reason) {
        Instant now = now();
        String actor = Long.toString(actorId(context));
        Long tenantId = context.member().tenantId();
        audit.record(() -> Outcome.succeeded(actor, tenantId, new Target(targetType, Long.toString(targetId)), reason,
            now));
    }

    /**
     * 登记发布或切换的成功审计。
     *
     * @param context 当前会话
     * @param releaseId 发布 ID
     * @param reason 有界说明
     */
    public void recordRelease(SessionContext context, long releaseId, String reason) {
        recordSucceeded(context, RELEASE_AUDIT_TYPE, releaseId, reason);
    }

    /**
     * 按编码读取应用；不存在返回 404。
     *
     * @param applicationCode 应用编码
     * @return 应用
     */
    public I18nApplication requireApplication(String applicationCode) {
        I18nApplication application = applications.findByCode(applicationCode);
        if (application == null) {
            throw notFound();
        }
        return application;
    }

    /**
     * 按代码读取语言；不存在返回 404。
     *
     * @param localeCode 语言代码
     * @return 语言
     */
    public I18nLocale requireLocale(String localeCode) {
        I18nLocale locale = locales.findByCode(localeCode);
        if (locale == null) {
            throw notFound();
        }
        return locale;
    }

    /** ENABLED 语言代码，按排序值、代码升序。 */
    public List<String> enabledLocales() {
        return locales.findAll().stream().filter(locale -> locale.getStatus() == LocaleStatus.ENABLED)
            .map(I18nLocale::getLocaleCode).toList();
    }

    /**
     * 评估应用当前文案：覆盖率、发布报告与 ICU 问题；预检、发布与应用详情共用。
     *
     * @param application 应用
     * @return 评估结果
     */
    public ReleaseAssessment assess(I18nApplication application) {
        return assess(application, currentTexts(application));
    }

    /**
     * 用已读取的当前文案评估应用：预检与发布读取一次文案，评估、变更摘要与语言包共用。
     *
     * @param application 应用
     * @param texts 当前文案
     * @return 评估结果
     */
    public ReleaseAssessment assess(I18nApplication application, CurrentTexts texts) {
        return ReleaseAssessor.assess(texts.getActiveKeys(), texts.getTranslations(), texts.getEnabledLocales(),
            Set.copyOf(LocalizationJson.readLocales(application.getRequiredLocalesJson())),
            application.getCoverageThresholdPercent());
    }

    /**
     * 读取应用的当前文案：ACTIVE key、这些 key 的翻译与 ENABLED 语言。
     *
     * @param application 应用
     * @return 当前文案
     */
    public CurrentTexts currentTexts(I18nApplication application) {
        return new CurrentTexts(keys.findByStatus(application.getApplicationId(), KeyStatus.ACTIVE),
            translations.findActiveByApplication(application.getApplicationId()), enabledLocales());
    }

    /** 应用资源锁 Key：同一应用的命名空间、导入、翻译、发布与切换共用。 */
    public static String applicationLock(String applicationCode) {
        return "localization:application:" + applicationCode;
    }

    /** 语言资源锁 Key。 */
    public static String localeLock(String localeCode) {
        return "localization:locale:" + localeCode;
    }

    /** TRY_LOCK 等待毫秒数。 */
    public int lockWaitMillis() {
        return Math.toIntExact(properties.getLockWaitTime().toMillis());
    }

    /** 资源锁租约毫秒数。 */
    public int lockLeaseMillis() {
        return Math.toIntExact(properties.getLockLeaseTime().toMillis());
    }

    /** 当前账号 ID。 */
    public long actorId(SessionContext context) {
        return context.account().id();
    }

    /** 下一个 Snowflake ID。 */
    public long nextId() {
        return ids.nextId();
    }

    /** 本次操作的 UTC 微秒时间。 */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 核对调用方读取的版本；不符返回 409。
     *
     * @param current 当前版本
     * @param expected 调用方读取的版本
     * @param subject 业务对象描述
     */
    public static void requireVersion(int current, int expected, String subject) {
        if (current != expected) {
            throw new ConcurrentRequestException(subject + " 已被其他操作修改，请刷新后重试，当前版本 " + current + "，请求版本 "
                + expected);
        }
    }

    /** 资源不存在。 */
    public static FailException notFound() {
        return new FailException(CommonErrorCode.NOT_FOUND);
    }
}
