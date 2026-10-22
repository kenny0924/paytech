package com.paytech.channel.connector.protocol.service.processor;

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
import com.paytech.channel.connector.protocol.core.dal.dataobject.ApiField;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChangeRecord;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChanges;
import com.paytech.channel.connector.protocol.domain.ConfigurationFieldChange;
import com.paytech.channel.connector.protocol.domain.FieldDefinition;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;

import lombok.AllArgsConstructor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 外部 API 配置 Processor 共用的纯转换与结果构造；不访问数据库或外部服务。 */
@Component
@AllArgsConstructor
public class ExternalApiProcessorSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    /**
     * 冻结配置变更记录：字段与地址只记录名称、编码或摘要，不写入完整定义
     * <li>字段摘要沿用原 Platform 审计的字段名与前后值，写入 change_detail；与业务变更由调用方在同一事务中保存。</li>
     *
     * @param targetType 被变更对象类型
     * @param targetId 被变更对象主键；删除时为删除前的主键
     * @param changeType CREATE、UPDATE 或 DELETE
     * @param actorId 操作者 Account ID
     * @param objectCode 对象编码，下级对象为 {渠道编码}/{对象编码}
     * @param beforeVersion 变更前版本，创建为 0
     * @param afterVersion 变更后版本；删除为删除前版本
     * @param changes 受控字段的前后摘要
     * @param reason 操作者原因
     * @param now 本次保存时间
     * @return 待写入的变更记录
     */
    ChannelConfigChangeRecord changed(ChangeTargetType targetType, long targetId, ChangeType changeType, long actorId,
        String objectCode, int beforeVersion, int afterVersion, List<ConfigurationFieldChange> changes, String reason,
        Instant now) {
        ChannelConfigChangeRecord record = ChannelConfigChanges.of(nextId(), targetType, targetId, changeType, actorId,
            now);
        record.setReason(reason);
        record.setChangeDetail(changeDetail(objectCode, beforeVersion, afterVersion, changes));
        return record;
    }

    /** 变更摘要 JSON：{objectCode, beforeVersion, afterVersion, changes[{field, before, after}]}，字段顺序固定。 */
    static String changeDetail(String objectCode, int beforeVersion, int afterVersion,
        List<ConfigurationFieldChange> changes) {
        ObjectNode detail = JSON.createObjectNode();
        detail.put("objectCode", objectCode);
        detail.put("beforeVersion", beforeVersion);
        detail.put("afterVersion", afterVersion);
        var items = detail.putArray("changes");
        for (ConfigurationFieldChange change : changes) {
            items.addObject().put("field", change.getField().getCode()).put("before", change.getBefore())
                .put("after", change.getAfter());
        }
        return JSON.writeValueAsString(detail);
    }
}
