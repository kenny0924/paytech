package com.paytech.platform.support.dataquery.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService.ManagementContext;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryLimitsProperties;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryManagementProperties;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.JointQueryParticipation;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypePatternRules;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;
import com.paytech.platform.support.dataquery.domainservice.service.QueryKeyTypeService;
import com.paytech.platform.support.dataquery.template.ParameterDeclarationReader;
import com.paytech.platform.support.dataquery.template.TemplateProblem;
import com.paytech.platform.support.dataquery.template.TemplateValidationResult;
import com.paytech.platform.support.service.processor.OperationsTransactionExecutor;

import lombok.AllArgsConstructor;

/**
 * 数据查询各 Processor 共用的权限、资源锁参数、事务与校验结果转换；不访问外部能力。
 * <li>Vault 与被查库访问前先在事务外确认权限，避免无权限请求触发外部调用；写事务内再次复核权限。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DataQueryManagementSupport {

    /** 查看数据源、模板。 */
    public static final String READ_PERMISSION = "DATA_QUERY_READ";

    /** 维护数据源。 */
    public static final String SOURCE_MANAGE_PERMISSION = "DATA_SOURCE_MANAGE";

    /** 维护模板、校验与试运行。 */
    public static final String TEMPLATE_MANAGE_PERMISSION = "DATA_QUERY_TEMPLATE_MANAGE";

    /** 执行任意 ACTIVE 模板。 */
    public static final String EXECUTE_PERMISSION = "DATA_QUERY_EXECUTE";

    private final Clock clock;
    private final IdGenerator ids;
    private final QueryKeyTypeService keyTypes;
    private final DataQueryLimitsProperties limits;
    private final OperationsTransactionExecutor transactions;
    private final DataQueryManagementProperties properties;
    private final OperationsManagementPolicyService policy;

    /** 同一数据源的修改、启停、删除与连接检查回写共用的锁。 */
    static String sourceLockKey(long dataSourceId) {
        return "lock:data-query:source:" + dataSourceId;
    }

    /** 新建数据源按编码串行；编码唯一索引仍为最终保护。 */
    static String sourceCodeLockKey(String dataSourceCode) {
        return "lock:data-query:source-code:" + dataSourceCode;
    }

    /** 同一模板的保存、恢复与启停共用的锁。 */
    static String templateLockKey(long templateId) {
        return "lock:data-query:template:" + templateId;
    }

    /** 新建模板按编码串行；编码唯一索引仍为最终保护。 */
    static String templateCodeLockKey(String templateCode) {
        return "lock:data-query:template-code:" + templateCode;
    }

    /** 同一键类型的修改与启停共用的锁；排他行锁与版本条件更新仍为最终保护。 */
    static String keyTypeLockKey(long keyTypeId) {
        return "lock:data-query:key-type:" + keyTypeId;
    }

    /** 新建键类型按编码串行；编码唯一索引仍为最终保护。 */
    static String keyTypeCodeLockKey(String keyTypeCode) {
        return "lock:data-query:key-type-code:" + keyTypeCode;
    }

    /** 键类型整体重排的锁。 */
    static String keyTypeOrderLockKey() {
        return "lock:data-query:key-type-order";
    }

    /** TRY_LOCK 等待毫秒数。 */
    int lockWaitMillis() {
        return Math.toIntExact(properties.lockWaitMillis());
    }

    /** 锁租约毫秒数。 */
    int lockLeaseMillis() {
        return Math.toIntExact(properties.lockLeaseMillis());
    }

    /** 本次写入的 UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 新主键。 */
    long nextId() {
        return ids.nextId();
    }

    /**
     * 不开事务重检会话与权限，用于查询与事务外的外部调用之前。
     *
     * @return 当前会话
     */
    SessionContext requirePermission(String actor, String session, String permission) {
        return policy.requirePermission(actor, session, permission);
    }

    /**
     * 写事务：先复核会话与权限，再执行本地写入与审计登记；事务内不访问外部能力。
     *
     * @param work 以授权上下文执行的本地写入
     * @return 写入结果
     */
    <T> T write(String actor, String session, String permission, Function<ManagementContext, T> work) {
        return transactions.write(() -> work.apply(policy.authorizeManagement(actor, session, null, List.of(), null,
            null, false, permission)));
    }

    /**
     * 草稿联合查询配置引用的键类型，供保存校验；没有配置时为空。不加锁，写入时由保存事务内的共享锁重证。
     *
     * @param draft 草稿
     * @return 编码到键类型
     */
    Map<String, QueryKeyTypeDetail> referencedKeyTypes(QueryTemplateDraft draft) {
        return draft.getJoint() == null ? Map.of() : keyTypes.findQueryKeyTypes(draft.getJoint().referencedKeyTypes());
    }

    /**
     * 保存与校验结果附加的联合查询参与提示（INFO 参与 / WARNING 不参与）；草稿没有联合查询配置或校验有错误时不附加。
     *
     * @param problems 已返回的问题
     * @param result 保存校验结果
     * @param draft 草稿
     * @param dataSource 草稿引用的数据源
     * @param templateActive 模板当前是否 ACTIVE；新建与编辑器校验按 ACTIVE
     * @return 校验问题加参与提示
     */
    static List<TemplateProblem> withParticipation(List<TemplateProblem> problems, TemplateValidationResult result,
        QueryTemplateDraft draft, DataSourceDetail dataSource, boolean templateActive) {
        if (draft.getJoint() == null || result.hasErrors()) {
            return problems;
        }
        JointQueryParticipation participation = JointQueryParticipation.evaluate(draft.getJoint(),
            new ParameterDeclarationReader().read(draft.getParameters()), templateActive,
            dataSource != null && dataSource.statusIs(DataSourceStatus.ACTIVE));
        List<TemplateProblem> combined = new ArrayList<>(problems);
        combined.add(participation.problem());
        return List.copyOf(combined);
    }

    /** 键类型入口正则不符合 Java / JavaScript 共同子集时返回 422，field 为 valuePattern。 */
    static void requireValidPattern(String valuePattern) {
        String violation = valuePattern == null ? null : QueryKeyTypePatternRules.violation(valuePattern);
        if (violation != null) {
            throw new RequestBodyValidationException(List.of(new ProblemFieldError("valuePattern", violation)), null);
        }
    }

    /** 键类型不存在返回 404。 */
    static QueryKeyTypeDetail requireFound(QueryKeyTypeDetail keyType) {
        if (keyType == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return keyType;
    }

    /** 连接池大小不超过系统上限。 */
    void requirePoolSize(int maxPoolSize) {
        if (maxPoolSize > limits.getMaxPoolSize()) {
            throw new BizCheckFailException("连接池最大连接数不能超过 " + limits.getMaxPoolSize());
        }
    }

    /** 数据源不存在返回 404。 */
    static DataSourceDetail requireFound(DataSourceDetail dataSource) {
        if (dataSource == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return dataSource;
    }

    /** 模板不存在返回 404。 */
    static QueryTemplateDetail requireFound(QueryTemplateDetail template) {
        if (template == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return template;
    }

    /**
     * 保存校验有 ERROR 时返回 422 VALIDATION_ERROR：正文问题的 field 为 body，message 带行列前缀。
     *
     * @param result 校验结果
     * @return 警告
     */
    static List<TemplateProblem> requireValid(TemplateValidationResult result) {
        List<ProblemFieldError> errors = result.getProblems().stream()
            .filter(problem -> problem.getLevel() == TemplateProblemLevel.ERROR)
            .map(problem -> new ProblemFieldError(problem.getField() == null ? "body" : problem.getField(),
                problem.getField() == null ? problem.describe() : problem.getMessage()))
            .toList();
        if (!errors.isEmpty()) {
            throw new RequestBodyValidationException(errors, null);
        }
        return result.getProblems().stream().filter(problem -> problem.getLevel() == TemplateProblemLevel.WARNING)
            .toList();
    }
}
