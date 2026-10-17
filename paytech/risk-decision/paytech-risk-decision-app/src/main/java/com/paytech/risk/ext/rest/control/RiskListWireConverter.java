package com.paytech.risk.ext.rest.control;

import java.time.Instant;
import java.util.Arrays;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.control.request.AddRiskListEntryRequestBody;
import com.paytech.risk.api.control.request.CreateRiskListRequestBody;
import com.paytech.risk.api.control.request.DeactivateRiskListEntryRequestBody;
import com.paytech.risk.api.control.response.RiskListEntryResponseBody;
import com.paytech.risk.api.control.response.RiskListResponseBody;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskListEntryView;
import com.paytech.risk.control.domain.list.RiskListView;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.control.service.processor.request.AddRiskListEntryRequest;
import com.paytech.risk.control.service.processor.request.CreateRiskListRequest;
import com.paytech.risk.control.service.processor.request.DeactivateRiskListEntryRequest;

/** 名单协议模型与 risk-control Owner 模型之间的转换。 */
final class RiskListWireConverter {
    /** 纯转换工具。 */
    private RiskListWireConverter() { }

    /** 创建名单请求转换；Code 已由 Body 注解限定为合法值。 */
    static CreateRiskListRequest toRequest(long actorAccountId, CreateRiskListRequestBody body) {
        return new CreateRiskListRequest(actorAccountId, body.getListCode(), body.getListName(),
            code(RiskSubjectType.values(), body.getSubjectType(), "subjectType"),
            code(RiskListPurpose.values(), body.getPurpose(), "purpose"));
    }

    /** 添加成员请求转换。 */
    static AddRiskListEntryRequest toRequest(long actorAccountId, String listCode, AddRiskListEntryRequestBody body) {
        return new AddRiskListEntryRequest(actorAccountId, listCode, body.getSubjectValue(), instant(body.getEffectiveFrom()),
            instant(body.getEffectiveTo()), body.getReason());
    }

    /** 失效成员请求转换。 */
    static DeactivateRiskListEntryRequest toRequest(long actorAccountId, String listCode, long entryId,
        DeactivateRiskListEntryRequestBody body) {
        return new DeactivateRiskListEntryRequest(actorAccountId, listCode, entryId, body.getReason());
    }

    /** 可选成员状态解析，未知 Code 为参数错误。 */
    static RiskListEntryStatus parseEntryStatus(String status) {
        return status == null ? null : code(RiskListEntryStatus.values(), status, "status");
    }

    /** 名单定义转换。 */
    static RiskListResponseBody toResponse(RiskListView view) {
        RiskListResponseBody body = new RiskListResponseBody();
        body.setListId(Long.toString(view.listId()));
        body.setListCode(view.listCode());
        body.setListName(view.listName());
        body.setSubjectType(view.subjectType().getCode());
        body.setPurpose(view.purpose());
        body.setActive(view.active());
        return body;
    }

    /** 名单成员转换，主体键已脱敏。 */
    static RiskListEntryResponseBody toResponse(RiskListEntryView view) {
        RiskListEntryResponseBody body = new RiskListEntryResponseBody();
        body.setEntryId(Long.toString(view.entryId()));
        body.setListId(Long.toString(view.listId()));
        body.setSubjectType(view.subjectType().getCode());
        body.setMaskedSubjectKey(view.maskedSubjectKey());
        body.setSourceType(view.sourceType());
        body.setStatus(view.status());
        body.setEffectiveFrom(view.effectiveFrom().toString());
        body.setEffectiveTo(view.effectiveTo() == null ? null : view.effectiveTo().toString());
        body.setReason(view.reason());
        body.setCreatedBy(view.createdBy() == null ? null : view.createdBy().toString());
        body.setDeactivatedBy(view.deactivatedBy() == null ? null : view.deactivatedBy().toString());
        body.setDeactivatedReason(view.deactivatedReason());
        body.setCreateTime(view.createTime().toString());
        return body;
    }

    /** 解析 UTC 时间，空值保持为空。 */
    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    /** 按稳定 Code 解析闭合枚举。 */
    private static <E extends Enum<E> & com.paytech.foundation.common.enums.CodeEnum> E code(E[] values, String code,
        String field) {
        return Arrays.stream(values).filter(value -> value.getCode().equals(code)).findFirst()
            .orElseThrow(() -> new InvalidParameterException(field + " 取值无效"));
    }
}
