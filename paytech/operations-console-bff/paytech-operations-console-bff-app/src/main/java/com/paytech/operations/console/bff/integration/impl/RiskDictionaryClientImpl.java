package com.paytech.operations.console.bff.integration.impl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskDictionaryClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskDictionaryModels.*;
import com.paytech.risk.api.decision.DecisionDictionaryApi;
import static com.paytech.operations.console.bff.integration.impl.RiskConfigurationResponsePolicy.*;
/** 字典请求精确转发，成功后核对响应身份、版本和当前内容。 */
@Component
@AllArgsConstructor
public class RiskDictionaryClientImpl implements RiskDictionaryClient {
    private final DecisionDictionaryApi api;
    /** 单次请求 Risk 按字面关键字和状态查询原因当前值分页，校验 Owner 响应且不自动重试。 */
    @Override public PagingResponseBody<RejectionReasonResponseBody> listRejectionReasons(OperatorContext o, int page, int size, String keyword, String status) {
        var owner = api.listRejectionReasons(o.accountId(), o.sessionId(), page, size, keyword, status);
        page(owner, page, size);
        var result = owner.map(RiskConfigurationConverter::reason);
        String last = null;
        for (var row : result.items()) { reason(row); filter(status, row.status());
            require(last == null || last.compareTo(row.reasonCode()) < 0); last = row.reasonCode(); }
        return result;
    }
    /** 单次请求 Risk 按不可变编码读取原因当前值，停用仍可查询，校验 Owner 响应且不自动重试。 */
    @Override public RejectionReasonResponseBody getRejectionReason(OperatorContext o, String code) {
        var result = RiskConfigurationConverter.reason(api.getRejectionReason(o.accountId(), o.sessionId(), code));
        reason(result); equal(code, result.reasonCode()); return result;
    }
    /** 单次请求 Risk 创建并启用唯一的原因编码，校验 Owner 响应且不自动重试。 */
    @Override public DictionaryWriteResponseBody createRejectionReason(OperatorContext o, CreateRejectionReasonRequestBody body) {
        var request = new com.paytech.risk.api.decision.request.CreateRejectionReasonRequestBody();
        request.setReasonCode(body.reasonCode());
        request.setName(body.name());
        request.setDescription(body.description());
        request.setDefaultMessage(body.defaultMessage());
        var result = RiskConfigurationConverter.write(api.createRejectionReason(o.accountId(), o.sessionId(), request));
        write(result, "REJECTION_REASON", body.reasonCode(), "1", o.accountId()); equal("ACTIVE", result.status()); return result;
    }
    /** 单次请求 Risk 按期望版本完整更新原因内容，保留编码与状态，校验 Owner 响应且不自动重试。 */
    @Override public DictionaryWriteResponseBody updateRejectionReason(OperatorContext o, String code, UpdateRejectionReasonRequestBody body) {
        var request = new com.paytech.risk.api.decision.request.UpdateRejectionReasonRequestBody();
        request.setExpectedVersion(body.expectedVersion());
        request.setName(body.name());
        request.setDescription(body.description());
        request.setDefaultMessage(body.defaultMessage());
        var result = RiskConfigurationConverter.write(api.updateRejectionReason(o.accountId(), o.sessionId(), code, request));
        write(result, "REJECTION_REASON", code, nextVersion(body.expectedVersion()), o.accountId()); return result;
    }
    /** 单次请求 Risk 按期望版本独立启停原因，校验 Owner 响应且不自动重试。 */
    @Override public DictionaryWriteResponseBody changeRejectionReasonStatus(OperatorContext o, String code, ChangeDictionaryStatusRequestBody body) {
        var request = new com.paytech.risk.api.decision.request.ChangeDictionaryStatusRequestBody();
        request.setExpectedVersion(body.expectedVersion());
        request.setStatus(body.status());
        var result = RiskConfigurationConverter.write(api.changeRejectionReasonStatus(o.accountId(), o.sessionId(), code, request));
        write(result, "REJECTION_REASON", code, nextVersion(body.expectedVersion()), o.accountId()); equal(body.status(), result.status()); return result;
    }
    /** 单次请求 Risk 按配置归属分页查询原因的精确保存项引用，校验 Owner 响应且不自动重试。 */
    @Override public PagingResponseBody<RejectionReasonReference> getRejectionReasonReferences(OperatorContext o, String code, int page, int size, String scope) {
        var owner = api.getRejectionReasonReferences(o.accountId(), o.sessionId(), code, page, size, scope);
        page(owner, page, size);
        var result = owner.map(RiskConfigurationConverter::reference);
        reasonReferences(result.items(), scope); return result;
    }
    /** 单次请求 Risk 按字面关键字和状态查询标签当前值分页，校验 Owner 响应且不自动重试。 */
    @Override public PagingResponseBody<SubjectTagResponseBody> listSubjectTags(OperatorContext o, int page, int size, String keyword, String status) {
        var owner = api.listSubjectTags(o.accountId(), o.sessionId(), page, size, keyword, status);
        page(owner, page, size);
        var result = owner.map(RiskConfigurationConverter::tag);
        String last = null;
        for (var row : result.items()) { tag(row); filter(status, row.status());
            require(last == null || last.compareTo(row.tagCode()) < 0); last = row.tagCode(); }
        return result;
    }
    /** 单次请求 Risk 按不可变编码读取标签当前值，停用仍可查询，校验 Owner 响应且不自动重试。 */
    @Override public SubjectTagResponseBody getSubjectTag(OperatorContext o, String code) {
        var result = RiskConfigurationConverter.tag(api.getSubjectTag(o.accountId(), o.sessionId(), code));
        tag(result); equal(code, result.tagCode()); return result;
    }
    /** 单次请求 Risk 创建并启用唯一的标签编码，校验 Owner 响应且不自动重试。 */
    @Override public DictionaryWriteResponseBody createSubjectTag(OperatorContext o, CreateSubjectTagRequestBody body) {
        var request = new com.paytech.risk.api.decision.request.CreateSubjectTagRequestBody();
        request.setTagCode(body.tagCode());
        request.setName(body.name());
        request.setDescription(body.description());
        var result = RiskConfigurationConverter.write(api.createSubjectTag(o.accountId(), o.sessionId(), request));
        write(result, "SUBJECT_TAG", body.tagCode(), "1", o.accountId()); equal("ACTIVE", result.status()); return result;
    }
    /** 单次请求 Risk 按期望版本完整更新标签内容，保留编码与状态，校验 Owner 响应且不自动重试。 */
    @Override public DictionaryWriteResponseBody updateSubjectTag(OperatorContext o, String code, UpdateSubjectTagRequestBody body) {
        var request = new com.paytech.risk.api.decision.request.UpdateSubjectTagRequestBody();
        request.setExpectedVersion(body.expectedVersion());
        request.setName(body.name());
        request.setDescription(body.description());
        var result = RiskConfigurationConverter.write(api.updateSubjectTag(o.accountId(), o.sessionId(), code, request));
        write(result, "SUBJECT_TAG", code, nextVersion(body.expectedVersion()), o.accountId()); return result;
    }
    /** 单次请求 Risk 按期望版本独立启停标签，校验 Owner 响应且不自动重试。 */
    @Override public DictionaryWriteResponseBody changeSubjectTagStatus(OperatorContext o, String code, ChangeDictionaryStatusRequestBody body) {
        var request = new com.paytech.risk.api.decision.request.ChangeDictionaryStatusRequestBody();
        request.setExpectedVersion(body.expectedVersion());
        request.setStatus(body.status());
        var result = RiskConfigurationConverter.write(api.changeSubjectTagStatus(o.accountId(), o.sessionId(), code, request));
        write(result, "SUBJECT_TAG", code, nextVersion(body.expectedVersion()), o.accountId()); equal(body.status(), result.status()); return result;
    }
    /** 单次请求 Risk 按配置归属分页查询标签的精确保存项引用，校验 Owner 响应且不自动重试。 */
    @Override public PagingResponseBody<DictionaryReference> getSubjectTagReferences(OperatorContext o, String code, int page, int size, String scope) {
        var owner = api.getSubjectTagReferences(o.accountId(), o.sessionId(), code, page, size, scope);
        page(owner, page, size);
        var result = owner.map(RiskConfigurationConverter::reference);
        references(result.items(), "SUBJECT_TAG", scope); return result;
    }
}
