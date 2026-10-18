package com.paytech.platform.support.integration.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.integration.core.dal.dataobject.ApiField;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.ValidationIssue;

import lombok.AllArgsConstructor;

/** 外部 API 配置 Processor 共用的纯转换与结果构造；不访问数据库或外部服务。 */
@Component
@AllArgsConstructor
public class ExternalApiProcessorSupport {

    private final Clock clock;
    private final IdGenerator ids;

    /** 新对象标识。 */
    long nextId() {
        return ids.nextId();
    }

    /** 本次保存的 UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 静态校验有问题时以 422 返回全部字段路径与说明。 */
    void requireValid(List<ValidationIssue> issues) {
        var blocking = issues.stream().filter(ValidationIssue::blocking).toList();
        if (blocking.isEmpty()) {
            return;
        }
        throw new RequestBodyValidationException(blocking.stream()
            .map(issue -> new ProblemFieldError(issue.getLocation(), issue.getMessage())).toList(), null);
    }

    /** 读取不到配置时统一返回 404。 */
    <T> T requireFound(T row) {
        if (row == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return row;
    }

    /** 版本不一致或已达上限时拒绝，不读取新版本重试。 */
    void requireVersion(int actual, int expected) {
        if (actual != expected || actual == Integer.MAX_VALUE) {
            throw new ConcurrentRequestException("外部 API 配置已被其他操作修改，请刷新后重试");
        }
    }

    /** 把字段定义按顺序转为某个 API、某个方向的待插入行，顺序号从 1 开始。 */
    List<ApiField> apiFields(long apiId, ApiFieldDirection direction, List<FieldDefinition> definitions,
        Instant now) {
        var rows = new ArrayList<ApiField>(definitions.size());
        for (int index = 0; index < definitions.size(); index++) {
            var definition = definitions.get(index);
            rows.add(new ApiField(nextId(), apiId, direction, definition.getLocation(), definition.getPath(),
                definition.getName(), definition.getDataType(), definition.isRequired(), index + 1,
                definition.getDescription(), now, now));
        }
        return rows;
    }

    /** 已保存字段中某方向的定义，保持顺序号顺序。 */
    List<FieldDefinition> definitions(List<ApiField> rows, ApiFieldDirection direction) {
        return rows.stream().filter(row -> row.getDirection() == direction)
            .map(row -> new FieldDefinition(row.getFieldLocation(), row.getFieldPath(), row.getFieldName(),
                row.getDataType(), row.isRequiredFlag(), row.getDescription()))
            .toList();
    }

    /** 冻结配置变更事实；字段与地址只记录摘要，不写入完整定义。 */
    ConfigurationChanged changed(String actor, long tenantId, String objectType, String code, int beforeVersion,
        int afterVersion, List<ConfigurationFieldChange> changes, String reason, Instant now) {
        return new ConfigurationChanged(new Identity(actor, tenantId), new Target(objectType, code), beforeVersion,
            afterVersion, changes, reason, now);
    }
}
