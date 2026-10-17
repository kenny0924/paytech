package com.paytech.risk.ext.rest.decision;

import static com.paytech.risk.ext.rest.decision.DecisionDictionaryWireConverter.*;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.DecisionDictionaryApi;
import com.paytech.risk.decision.api.request.*;
import com.paytech.risk.decision.api.response.*;
import com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;
import com.paytech.risk.decision.service.processor.*;
import com.paytech.risk.decision.service.processor.request.*;
import com.paytech.foundation.web.request.OperationRequestContextParser;

import lombok.AllArgsConstructor;

/** 可信身份与字典协议解析，无数据库访问或业务事务。 */
@RestController
@Validated
@AllArgsConstructor
public class DecisionDictionaryController implements DecisionDictionaryApi {
    private final SubjectTagListProcessor subjectTagListProcessor;
    private final CreateSubjectTagProcessor createSubjectTagProcessor;
    private final SubjectTagDetailProcessor subjectTagDetailProcessor;
    private final UpdateSubjectTagProcessor updateSubjectTagProcessor;
    private final RejectionReasonListProcessor rejectionReasonListProcessor;
    private final SubjectTagReferencesProcessor subjectTagReferencesProcessor;
    private final CreateRejectionReasonProcessor createRejectionReasonProcessor;
    private final RejectionReasonDetailProcessor rejectionReasonDetailProcessor;
    private final UpdateRejectionReasonProcessor updateRejectionReasonProcessor;
    private final ChangeSubjectTagStatusProcessor changeSubjectTagStatusProcessor;
    private final OperationRequestContextParser parser;
    private final RejectionReasonReferencesProcessor rejectionReasonReferencesProcessor;
    private final ChangeRejectionReasonStatusProcessor changeRejectionReasonStatusProcessor;

    /** 解析可信身份及可选状态，委托 Decision 模块查询原因当前值分页。 */
    @Override
    public PagingResponseBody<RejectionReasonResponseBody> listRejectionReasons(String actorAccountId, String sessionId,
        Integer page, Integer size, String keyword, String status) {
        parser.parse(actorAccountId, sessionId);
        var request = new RejectionReasonListRequest(page, size, keyword, status(status));
        return rejectionReasonListProcessor.process(request).map(DecisionDictionaryWireConverter::reason);
    }
    /** 解析可信身份并委托详情查询，将领域未找到映射为既有 404 合同。 */
    @Override
    public RejectionReasonResponseBody getRejectionReason(String actorAccountId, String sessionId, String reasonCode) {
        parser.parse(actorAccountId, sessionId);
        var request = new RejectionReasonDetailRequest(reasonCode);
        try {
            return reason(rejectionReasonDetailProcessor.process(request));
        } catch (DictionaryConflictException failure) {
            throw failure(failure);
        }
    }

    /** 将可信操作者和创建内容交给 Decision 模块，保持原编码重复错误合同。 */
    @Override
    public DictionaryWriteResponseBody createRejectionReason(String actorAccountId, String sessionId,
        CreateRejectionReasonRequestBody body) {
        var context = parser.parse(actorAccountId, sessionId);
        var request = new CreateRejectionReasonRequest(body.getReasonCode(), body.getName(), body.getDescription(),
            body.getDefaultMessage(), context.getActorAccountId());
        try {
            return result(createRejectionReasonProcessor.process(request));
        } catch (DictionaryConflictException failure) {
            throw failure(failure);
        }
    }

    /** 适配可信身份及命令内容，由模块 Processor 校验、协调并保存。 */
    @Override
    public DictionaryWriteResponseBody updateRejectionReason(String actorAccountId, String sessionId, String reasonCode,
        UpdateRejectionReasonRequestBody body) {
        var context = parser.parse(actorAccountId, sessionId);
        var request = new UpdateRejectionReasonRequest(reasonCode,
            parser.parsePositiveId("expectedVersion", body.getExpectedVersion()), body.getName(), body.getDescription(),
            body.getDefaultMessage(), context.getActorAccountId());
        try {
            return result(updateRejectionReasonProcessor.process(request));
        } catch (DictionaryConflictException exception) {
            throw failure(exception);
        }
    }

    /** 适配可信身份及命令内容，由模块 Processor 校验、协调并保存。 */
    @Override
    public DictionaryWriteResponseBody changeRejectionReasonStatus(String actorAccountId, String sessionId,
        String reasonCode, ChangeDictionaryStatusRequestBody body) {
        var context = parser.parse(actorAccountId, sessionId);
        var request = new ChangeRejectionReasonStatusRequest(reasonCode,
            parser.parsePositiveId("expectedVersion", body.getExpectedVersion()), status(body.getStatus()),
            context.getActorAccountId());
        try {
            return result(changeRejectionReasonStatusProcessor.process(request));
        } catch (DictionaryConflictException exception) {
            throw failure(exception);
        }
    }

    /** 解析可信身份，委托模块查询字典精确引用及当前发布关系。 */
    @Override
    public PagingResponseBody<RejectionReasonReferenceResponseBody> getRejectionReasonReferences(String actorAccountId,
        String sessionId,
        String reasonCode, Integer page, Integer size, String scope) {
        parser.parse(actorAccountId, sessionId);
        var request = new RejectionReasonReferencesRequest(reasonCode, page, size, scope);
        try {
            return DecisionConfigurationQueryWireConverter
                .reasonReferences(rejectionReasonReferencesProcessor.process(request));
        } catch (DictionaryConflictException exception) {
            throw failure(exception);
        } catch (com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException exception) {
            throw new FailException(RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND, null,
                exception);
        }
    }

    /** 解析可信身份和可选状态，委托模块分页查询当前标签。 */
    @Override
    public PagingResponseBody<SubjectTagResponseBody> listSubjectTags(String actorAccountId, String sessionId,
        Integer page, Integer size, String keyword, String status) {
        parser.parse(actorAccountId, sessionId);
        return subjectTagListProcessor.process(new SubjectTagListRequest(page, size, keyword, status(status)))
            .map(DecisionDictionaryWireConverter::tag);
    }

    /** 查询当前标签，停用仍可读取，未登记映射为字典专属错误。 */
    @Override
    public SubjectTagResponseBody getSubjectTag(String actorAccountId, String sessionId, String tagCode) {
        parser.parse(actorAccountId, sessionId);
        try {
            return tag(subjectTagDetailProcessor.process(new SubjectTagDetailRequest(tagCode)));
        } catch (DictionaryConflictException exception) {
            throw failure(exception);
        }
    }

    /** 适配可信身份及命令内容，由模块 Processor 校验、协调并保存。 */
    @Override
    public DictionaryWriteResponseBody createSubjectTag(String actorAccountId, String sessionId,
        CreateSubjectTagRequestBody body) {
        var context = parser.parse(actorAccountId, sessionId);
        var request = new CreateSubjectTagRequest(body.getTagCode(), body.getName(), body.getDescription(),
            context.getActorAccountId());
        try {
            return result(createSubjectTagProcessor.process(request));
        } catch (DictionaryConflictException exception) {
            throw failure(exception);
        }
    }

    /** 适配可信身份及命令内容，由模块 Processor 校验、协调并保存。 */
    @Override
    public DictionaryWriteResponseBody updateSubjectTag(String actorAccountId, String sessionId, String tagCode,
        UpdateSubjectTagRequestBody body) {
        var context = parser.parse(actorAccountId, sessionId);
        var request =
            new UpdateSubjectTagRequest(tagCode, parser.parsePositiveId("expectedVersion", body.getExpectedVersion()),
                body.getName(), body.getDescription(), context.getActorAccountId());
        try {
            return result(updateSubjectTagProcessor.process(request));
        } catch (DictionaryConflictException exception) {
            throw failure(exception);
        }
    }

    /** 适配可信身份及命令内容，由模块 Processor 校验、协调并保存。 */
    @Override
    public DictionaryWriteResponseBody changeSubjectTagStatus(String actorAccountId, String sessionId, String tagCode,
        ChangeDictionaryStatusRequestBody body) {
        var context = parser.parse(actorAccountId, sessionId);
        var request = new ChangeSubjectTagStatusRequest(tagCode,
            parser.parsePositiveId("expectedVersion", body.getExpectedVersion()), status(body.getStatus()),
            context.getActorAccountId());
        try {
            return result(changeSubjectTagStatusProcessor.process(request));
        } catch (DictionaryConflictException exception) {
            throw failure(exception);
        }
    }

    /** 解析可信身份，委托模块查询字典精确引用及当前发布关系。 */
    @Override
    public PagingResponseBody<SubjectTagReferenceResponseBody> getSubjectTagReferences(String actorAccountId,
        String sessionId,
        String tagCode, Integer page, Integer size, String scope) {
        parser.parse(actorAccountId, sessionId);
        var request = new SubjectTagReferencesRequest(tagCode, page, size, scope);
        try {
            return DecisionConfigurationQueryWireConverter.references(subjectTagReferencesProcessor.process(request));
        } catch (DictionaryConflictException exception) {
            throw failure(exception);
        } catch (com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException exception) {
            throw new FailException(RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND, null,
                exception);
        }
    }
}
