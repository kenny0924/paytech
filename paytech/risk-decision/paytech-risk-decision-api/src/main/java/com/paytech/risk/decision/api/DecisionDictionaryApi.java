package com.paytech.risk.decision.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.request.*;
import com.paytech.risk.decision.api.response.*;

import jakarta.validation.Valid;

/** Risk 原因与标签维护的私有 HTTP 合同；字典写操作不使用幂等键。 */
@HttpExchange("/decision-dictionaries/v1")
public interface DecisionDictionaryApi {

    /**
     * 按原因编码或名称的字面包含条件及状态查询当前原因分页。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param page
     *            页码，缺省为 1，范围为 1–100000
     * @param size
     *            每页条数，缺省为 10，只允许 10、20、50、100、200
     * @param keyword
     *            可选关键词，1–100 个 Unicode 码点，不允许首尾空白或控制字符；通配符按字面匹配
     * @param status
     *            可选 ACTIVE 或 DISABLED，省略查询全部，空值或未知值返回 400
     * @return 按原因编码、主键升序的分页；空页保留请求页码和页大小，total 与当前页来自同一快照
     */
    @GetExchange("/rejection-reasons")
    PagingResponseBody<RejectionReasonResponseBody> listRejectionReasons(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "status", required = false) String status);

    /**
     * 按永久原因编码读取当前字典详情，停用记录仍可查询。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param reasonCode
     *            1–64 位原因编码，以大写字母开头，仅允许大写字母、数字和下划线；格式无效返回 400
     * @return 当前内容、状态及审计信息；未登记返回 404 RISK_DICTIONARY_NOT_FOUND
     */
    @GetExchange("/rejection-reasons/{reasonCode}")
    RejectionReasonResponseBody getRejectionReason(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("reasonCode") String reasonCode);

    /**
     * 创建并启用拒绝原因，初始修订号为 1，编码永久唯一且停用后不可复用。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param body
     *            完整创建内容；description 键必填但允许 null，空串保存为 null
     * @return 本次提交的结果；重复编码返回 409 RISK_DICTIONARY_CODE_EXISTS，不回放历史结果
     */
    @PostExchange("/rejection-reasons")
    DictionaryWriteResponseBody createRejectionReason(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateRejectionReasonRequestBody body);

    /**
     * 完整更新原因内容并保留编码、状态；成功递增一版。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param reasonCode
     *            永久原因编码
     * @param body
     *            完整可编辑内容及 expectedVersion，过期版本返回 409 RISK_DICTIONARY_CHANGED
     * @return 本次提交结果；未登记返回 404，失败不改变原记录
     */
    @PutExchange("/rejection-reasons/{reasonCode}")
    DictionaryWriteResponseBody updateRejectionReason(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("reasonCode") String reasonCode,
        @Valid @RequestBody UpdateRejectionReasonRequestBody body);

    /**
     * 按期望版本启停原因，已有引用不阻止停用。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param reasonCode
     *            永久原因编码
     * @param body
     *            expectedVersion 及 ACTIVE/DISABLED；同值操作也递增修订号
     * @return 本次提交结果；未登记返回 404，版本冲突返回 409
     */
    @PostExchange("/rejection-reasons/{reasonCode}/status")
    DictionaryWriteResponseBody changeRejectionReasonStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("reasonCode") String reasonCode,
        @Valid @RequestBody ChangeDictionaryStatusRequestBody body);

    /**
     * 统一分页读取原因在规则和流程中的精确保存项引用。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param reasonCode
     *            永久原因编码；停用仍可查询，未登记返回 404
     * @param page
     *            页码，缺省 1，范围 1–100000
     * @param size
     *            缺省 10，只允许 10、20、50、100、200
     * @param scope
     *            ALL、CURRENT、HISTORY、DELETED；缺省 ALL
     * @return 同一快照内的引用分页，保留来源互斥字段及保存时名称
     */
    @GetExchange("/rejection-reasons/{reasonCode}/references")
    PagingResponseBody<RejectionReasonReferenceResponseBody> getRejectionReasonReferences(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("reasonCode") String reasonCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "scope", required = false) String scope);

    /**
     * 按编码或名称字面包含及状态查询当前标签分页。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param page
     *            页码，缺省 1，范围 1–100000
     * @param size
     *            缺省 10，只允许 10、20、50、100、200
     * @param keyword
     *            可选 1–100 个码点的字面关键词，不允许首尾空白
     * @param status
     *            可选 ACTIVE/DISABLED，省略查询全部
     * @return 按编码、主键升序的分页；计数与当前页来自同一快照
     */
    @GetExchange("/subject-tags")
    PagingResponseBody<SubjectTagResponseBody> listSubjectTags(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "status", required = false) String status);

    /**
     * 按永久编码读取当前标签，停用记录仍可查询。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param tagCode
     *            1–64 位大写字母、数字和下划线，以字母开头
     * @return 当前内容及审计信息；未登记返回 404 RISK_DICTIONARY_NOT_FOUND
     */
    @GetExchange("/subject-tags/{tagCode}")
    SubjectTagResponseBody getSubjectTag(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("tagCode") String tagCode);

    /**
     * 创建并启用主体标签，编码永久唯一且停用后不可复用。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param body
     *            完整创建内容；description 键必填但允许 null，空串保存为 null
     * @return 状态 ACTIVE、修订号 1 的本次结果；重复编码返回 409
     */
    @PostExchange("/subject-tags")
    DictionaryWriteResponseBody createSubjectTag(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateSubjectTagRequestBody body);

    /**
     * 完整更新标签名称和说明，保留编码及状态。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param tagCode
     *            永久标签编码
     * @param body
     *            完整可编辑内容及 expectedVersion，空说明保存为 null
     * @return 本次提交结果；未登记返回 404，版本冲突返回 409
     */
    @PutExchange("/subject-tags/{tagCode}")
    DictionaryWriteResponseBody updateSubjectTag(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("tagCode") String tagCode,
        @Valid @RequestBody UpdateSubjectTagRequestBody body);

    /**
     * 按期望版本启停标签，已有引用不阻止停用。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param tagCode
     *            永久标签编码
     * @param body
     *            expectedVersion 及 ACTIVE/DISABLED；同值操作也递增修订号
     * @return 本次提交结果；未登记返回 404，版本冲突返回 409
     */
    @PostExchange("/subject-tags/{tagCode}/status")
    DictionaryWriteResponseBody changeSubjectTagStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("tagCode") String tagCode,
        @Valid @RequestBody ChangeDictionaryStatusRequestBody body);

    /**
     * 读取标签在规则动作中的精确保存项和当前活动环境。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param tagCode
     *            永久标签编码；停用仍可查询，未登记返回 404
     * @param page
     *            页码，缺省 1，范围 1–100000
     * @param size
     *            缺省 10，只允许 10、20、50、100、200
     * @param scope
     *            ALL、CURRENT、HISTORY、DELETED；缺省 ALL
     * @return 同一快照内的引用分页，保留保存时名称和精确版本身份
     */
    @GetExchange("/subject-tags/{tagCode}/references")
    PagingResponseBody<SubjectTagReferenceResponseBody> getSubjectTagReferences(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("tagCode") String tagCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "scope", required = false) String scope);
}
