package com.paytech.channel.connector.protocol.service.processor;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.channel.connector.api.enums.VerifyCaseStatus;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyRun;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionSnapshot;
import com.paytech.channel.connector.protocol.domain.execution.StandardApiNotSentException;
import com.paytech.channel.connector.protocol.domain.verify.VerifyAccountView;
import com.paytech.channel.connector.protocol.domain.verify.VerifyCaseView;
import com.paytech.channel.connector.protocol.domain.verify.VerifyRunBriefView;
import com.paytech.channel.connector.protocol.domainservice.validator.VerifyCaseValidator;
import com.paytech.channel.connector.protocol.domainservice.verify.VerifyTemplateRenderer;

import lombok.AllArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 沙箱验证 Processor 共用的转换与只读计算（沙箱验证技术方案第 3～5 节）：账号材料校验、JSON 编解码、
 * 用例校验所需的实现合同、模板示例值与验证状态。只读本库（经执行快照加载器），不调用外部服务。
 */
@Component
@AllArgsConstructor
public class VerifySupport {

    /** 账号身份中占位的 Vault 引用；验证运行改读验证账号，不读取该值。 */
    public static final String VERIFY_VAULT_TOKEN = "VERIFY_ACCOUNT_NOT_USED";

    static final Pattern ENTRY_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    static final int MAX_ENTRIES = 64;
    static final int MAX_VALUE = 8192;
    static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final ObjectMapper json;
    private final ExecutionSnapshotLoader snapshots;

    /** 账号参数与凭据：最多 64 项，名称以字母开头只含字母、数字、_ . -，值 1～8192 字符。 */
    List<ValidationIssue> accountIssues(Map<String, String> parameters, Map<String, String> credentials) {
        var issues = new ArrayList<ValidationIssue>();
        entries("parameters", parameters, issues);
        entries("credentials", credentials, issues);
        return issues;
    }

    static void entries(String location, Map<String, String> values, List<ValidationIssue> issues) {
        if (values.size() > MAX_ENTRIES) {
            issues.add(new ValidationIssue("ACCOUNT_ENTRIES_INVALID", location, "最多 " + MAX_ENTRIES + " 项"));
        }
        values.forEach((name, value) -> {
            if (!ENTRY_NAME.matcher(name).matches()) {
                issues.add(new ValidationIssue("ACCOUNT_ENTRIES_INVALID", location + "." + name,
                    "名称须以字母开头，只含字母、数字、_ . -，最多 64 字符"));
            } else if (value == null || value.isBlank() || value.length() > MAX_VALUE) {
                issues.add(new ValidationIssue("ACCOUNT_ENTRIES_INVALID", location + "." + name, "值必须为 1～8192 字符"));
            }
        });
    }

    /** 字符串映射序列化为 JSON 对象，保持顺序。 */
    String mapJson(Map<String, String> values) {
        return json.writeValueAsString(new LinkedHashMap<>(values));
    }

    /** 解析已保存的字符串映射。 */
    Map<String, String> map(String value) {
        return Collections.unmodifiableMap(json.readValue(value, STRING_MAP));
    }

    /** 解析已保存的 JSON。 */
    JsonNode tree(String value) {
        return value == null ? null : json.readTree(value);
    }

    /** 任意值序列化为 JSON。 */
    String text(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }

    /** 任意值转为 JSON 树。 */
    JsonNode node(Object value) {
        return json.valueToTree(value);
    }

    /** 账号身份对象，供模板变量 account.identity 使用。 */
    JsonNode identity(IntegrationVerifyAccount account) {
        ObjectNode identity = json.createObjectNode();
        identity.put("channelCode", account.getChannelCode());
        identity.put("channelAccountId", account.getChannelAccountId());
        identity.put("entityNo", account.getEntityNo());
        identity.put("accountVaultToken", VERIFY_VAULT_TOKEN);
        return identity;
    }

    /** 保存时渲染模板用的示例值。 */
    VerifyTemplateRenderer.Scope sampleScope(IntegrationVerifyAccount account) {
        return new VerifyTemplateRenderer.Scope("1000000000000000001", "2026-01-01T00:00:00Z", identity(account),
            Map.of());
    }

    /**
     * 步骤中出现的标准 API 在本接入方的实现合同；没有实现或配置未通过运行前校验的不在结果中。
     *
     * @param integrationCode 接入方编码
     * @param steps steps_json（可能不合法，只取字符串编码）
     * @return 标准 API 编码 → 合同
     */
    Map<String, VerifyCaseValidator.StepContract> contracts(String integrationCode, JsonNode steps) {
        var result = new LinkedHashMap<String, VerifyCaseValidator.StepContract>();
        for (String code : standardApiCodes(steps)) {
            ExecutionSnapshot snapshot = load(code, integrationCode);
            if (snapshot != null) {
                result.put(code, new VerifyCaseValidator.StepContract(snapshot.getInputSchema(),
                    snapshot.getOutputSchema(), snapshot.getFlow().getReturns().keySet()));
            }
        }
        return result;
    }

    /** 步骤涉及的标准 API 编码，按步骤顺序去重。 */
    static List<String> standardApiCodes(JsonNode steps) {
        var codes = new LinkedHashSet<String>();
        if (steps != null && steps.isArray()) {
            steps.forEach(step -> {
                if (step.path("standardApiCode").isString()) {
                    codes.add(step.path("standardApiCode").asString());
                }
            });
        }
        return List.copyOf(codes);
    }

    /** 加载执行快照；不存在或配置未通过运行前校验时为 null。 */
    ExecutionSnapshot load(String standardApiCode, String integrationCode) {
        try {
            return snapshots.load(standardApiCode, integrationCode);
        } catch (StandardApiNotSentException | FailException | ErrorException unavailable) {
            return null;
        }
    }

    /**
     * 运行后用例、账号或引用配置是否已变更（沙箱验证技术方案第 5 节）。
     *
     * @param run 已结束的运行
     * @param currentCase 当前用例；已删除为 null
     * @param currentAccount 当前账号；已删除为 null
     * @param integrationCode 接入方编码
     * @param current 本次请求内的当前配置版本缓存：标准 API 编码 → 版本 JSON（加载失败为 null）
     * @return 已变更为 true
     */
    boolean stale(IntegrationVerifyRun run, IntegrationVerifyCase currentCase, IntegrationVerifyAccount currentAccount,
        String integrationCode, Map<String, JsonNode> current) {
        JsonNode snapshot = tree(run.getCaseSnapshotJson());
        JsonNode recorded = tree(run.getConfigVersionsJson());
        if (currentCase == null || currentAccount == null || recorded == null
            || snapshot.path("caseVersion").asInt() != currentCase.getVersion()
            || snapshot.path("accountId").asLong() != currentAccount.getVerifyAccountId()
            || snapshot.path("accountVersion").asInt() != currentAccount.getVersion()) {
            return true;
        }
        for (JsonNode step : snapshot.path("steps")) {
            String code = step.path("standardApiCode").asString();
            JsonNode now = current.computeIfAbsent(code, key -> {
                ExecutionSnapshot loaded = load(key, integrationCode);
                return loaded == null ? null : node(loaded.getConfigVersions());
            });
            if (now == null || !now.equals(recorded.get(step.path("id").asString()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证状态：运行中的最近运行优先；否则看最近一次已结束的运行。
     *
     * @param latest 最近一次运行；没有为 null
     * @param finished 最近一次已结束的运行；没有为 null
     * @param stale 已结束运行之后是否发生变更（只在其通过时使用）
     * @return 验证状态
     */
    static VerifyCaseStatus status(IntegrationVerifyRun latest, IntegrationVerifyRun finished, boolean stale) {
        if (latest != null && latest.getRunStatus() == VerifyRunStatus.RUNNING) {
            return VerifyCaseStatus.RUNNING;
        }
        if (finished == null) {
            return VerifyCaseStatus.NOT_RUN;
        }
        if (finished.getRunStatus() != VerifyRunStatus.PASSED) {
            return VerifyCaseStatus.FAILED;
        }
        return stale ? VerifyCaseStatus.STALE : VerifyCaseStatus.PASSED;
    }

    /** 用例视图。 */
    VerifyCaseView caseView(IntegrationVerifyCase row, String accountCode, VerifyCaseStatus status,
        IntegrationVerifyRun latest) {
        JsonNode steps = tree(row.getStepsJson());
        return new VerifyCaseView(row.getCaseCode(), row.getCaseName(), row.getDescription(), accountCode, steps,
            standardApiCodes(steps), status, brief(latest), row.getVersion(), row.getCreateTime(),
            row.getUpdateTime(), row.getUpdatedBy());
    }

    static VerifyRunBriefView brief(IntegrationVerifyRun run) {
        return run == null ? null
            : new VerifyRunBriefView(run.getVerifyRunId(), run.getRunStatus(), run.getStartTime(), run.getFinishTime());
    }

    /** 账号视图；列表项不带参数与凭据。 */
    VerifyAccountView accountView(IntegrationVerifyAccount row, List<String> referencedCaseCodes, boolean detail) {
        return new VerifyAccountView(row.getAccountCode(), row.getAccountName(), row.getChannelCode(),
            row.getChannelAccountId(), row.getEntityNo(), detail ? map(row.getParameters()) : Map.of(),
            detail ? map(row.getCredentials()) : Map.of(), referencedCaseCodes, row.getVersion(), row.getCreateTime(),
            row.getUpdateTime(), row.getUpdatedBy());
    }

    /** 运行编号：运行标识的十进制字符串；不合法为 null。 */
    static Long runId(String runCode) {
        return runCode != null && runCode.matches("[1-9][0-9]{0,18}") ? Long.valueOf(runCode) : null;
    }

    /** 用例快照：运行原因、用例与账号版本、步骤（不含凭据）。 */
    String caseSnapshot(IntegrationVerifyCase verifyCase, IntegrationVerifyAccount account, String integrationCode,
        String reason) {
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("reason", reason);
        snapshot.put("integrationCode", integrationCode);
        snapshot.put("caseCode", verifyCase.getCaseCode());
        snapshot.put("caseName", verifyCase.getCaseName());
        snapshot.put("caseVersion", verifyCase.getVersion());
        snapshot.put("accountId", account.getVerifyAccountId());
        snapshot.put("accountCode", account.getAccountCode());
        snapshot.put("accountVersion", account.getVersion());
        snapshot.set("steps", tree(verifyCase.getStepsJson()));
        return json.writeValueAsString(snapshot);
    }

    /** UTC 时间文本。 */
    static String time(Instant instant) {
        return instant.toString();
    }
}
