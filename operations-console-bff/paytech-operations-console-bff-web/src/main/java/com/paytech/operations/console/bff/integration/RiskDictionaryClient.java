package com.paytech.operations.console.bff.integration;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskDictionaryModels.*;
/** 已授权的字典单次 Owner 调用；没有重试或业务事务。 */
public interface RiskDictionaryClient {
    /** 单次请求 Risk 按字面关键字和状态查询原因当前值分页，校验 Owner 响应且不自动重试。 */
    PagingResponseBody<RejectionReasonResponseBody> listRejectionReasons(OperatorContext operator, int page, int size, String keyword, String status);
    /** 单次请求 Risk 按不可变编码读取原因当前值，停用仍可查询，校验 Owner 响应且不自动重试。 */
    RejectionReasonResponseBody getRejectionReason(OperatorContext operator, String reasonCode);
    /** 单次请求 Risk 创建并启用唯一的原因编码，校验 Owner 响应且不自动重试。 */
    DictionaryWriteResponseBody createRejectionReason(OperatorContext operator, CreateRejectionReasonRequestBody body);
    /** 单次请求 Risk 按期望版本完整更新原因内容，保留编码与状态，校验 Owner 响应且不自动重试。 */
    DictionaryWriteResponseBody updateRejectionReason(OperatorContext operator, String reasonCode, UpdateRejectionReasonRequestBody body);
    /** 单次请求 Risk 按期望版本独立启停原因，校验 Owner 响应且不自动重试。 */
    DictionaryWriteResponseBody changeRejectionReasonStatus(OperatorContext operator, String reasonCode, ChangeDictionaryStatusRequestBody body);
    /** 单次请求 Risk 按配置归属分页查询原因的精确保存项引用，校验 Owner 响应且不自动重试。 */
    PagingResponseBody<RejectionReasonReference> getRejectionReasonReferences(OperatorContext operator, String reasonCode, int page, int size, String scope);
    /** 单次请求 Risk 按字面关键字和状态查询标签当前值分页，校验 Owner 响应且不自动重试。 */
    PagingResponseBody<SubjectTagResponseBody> listSubjectTags(OperatorContext operator, int page, int size, String keyword, String status);
    /** 单次请求 Risk 按不可变编码读取标签当前值，停用仍可查询，校验 Owner 响应且不自动重试。 */
    SubjectTagResponseBody getSubjectTag(OperatorContext operator, String tagCode);
    /** 单次请求 Risk 创建并启用唯一的标签编码，校验 Owner 响应且不自动重试。 */
    DictionaryWriteResponseBody createSubjectTag(OperatorContext operator, CreateSubjectTagRequestBody body);
    /** 单次请求 Risk 按期望版本完整更新标签内容，保留编码与状态，校验 Owner 响应且不自动重试。 */
    DictionaryWriteResponseBody updateSubjectTag(OperatorContext operator, String tagCode, UpdateSubjectTagRequestBody body);
    /** 单次请求 Risk 按期望版本独立启停标签，校验 Owner 响应且不自动重试。 */
    DictionaryWriteResponseBody changeSubjectTagStatus(OperatorContext operator, String tagCode, ChangeDictionaryStatusRequestBody body);
    /** 单次请求 Risk 按配置归属分页查询标签的精确保存项引用，校验 Owner 响应且不自动重试。 */
    PagingResponseBody<DictionaryReference> getSubjectTagReferences(OperatorContext operator, String tagCode, int page, int size, String scope);
}
