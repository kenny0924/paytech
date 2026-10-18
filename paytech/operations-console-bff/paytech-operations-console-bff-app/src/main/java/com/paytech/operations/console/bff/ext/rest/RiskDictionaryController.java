package com.paytech.operations.console.bff.ext.rest;
import lombok.AllArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.paytech.foundation.web.paging.*;
import com.paytech.operations.console.bff.integration.RiskDictionaryClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskDictionaryModels.*;
/** 字典页面入口；授权由统一 PLT-I02 拦截器负责。 */
@RestController
@AllArgsConstructor
@RequestMapping("/decision-dictionaries/v1")
public class RiskDictionaryController {
    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";
    private final RiskDictionaryClient client;
    /** 完成当前路由授权后按字面关键字和状态查询原因当前值分页，返回 BFF 本地模型。 */
    @GetMapping("/rejection-reasons")
    public PagingResponseBody<RejectionReasonResponseBody> listRejectionReasons(OperatorContext o, @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size, @RequestParam(required = false) @com.paytech.operations.console.bff.validation.DictionaryText(max = 100, nullable = true, trimmed = true) String keyword,
        @RequestParam(required = false) @Pattern(regexp = "ACTIVE|DISABLED") String status) {
        var p = PagingRequestBody.of(page, size); return client.listRejectionReasons(o, p.page(), p.size(), keyword, status);
    }
    /** 完成当前路由授权后按不可变编码读取原因当前值，停用仍可查询，返回 BFF 本地模型。 */
    @GetMapping("/rejection-reasons/{reasonCode}")
    public RejectionReasonResponseBody getRejectionReason(OperatorContext o, @PathVariable @Pattern(regexp = CODE) String reasonCode) { return client.getRejectionReason(o, reasonCode); }
    /** 完成当前路由授权后创建并启用唯一的原因编码，返回 BFF 本地模型。 */
    @PostMapping("/rejection-reasons")
    public DictionaryWriteResponseBody createRejectionReason(OperatorContext o, @Valid @RequestBody CreateRejectionReasonRequestBody body) { return client.createRejectionReason(o, body); }
    /** 完成当前路由授权后按期望版本完整更新原因内容，保留编码与状态，返回 BFF 本地模型。 */
    @PutMapping("/rejection-reasons/{reasonCode}")
    public DictionaryWriteResponseBody updateRejectionReason(OperatorContext o, @PathVariable @Pattern(regexp = CODE) String reasonCode, @Valid @RequestBody UpdateRejectionReasonRequestBody body) { return client.updateRejectionReason(o, reasonCode, body); }
    /** 完成当前路由授权后按期望版本独立启停原因，返回 BFF 本地模型。 */
    @PostMapping("/rejection-reasons/{reasonCode}/status")
    public DictionaryWriteResponseBody changeRejectionReasonStatus(OperatorContext o, @PathVariable @Pattern(regexp = CODE) String reasonCode, @Valid @RequestBody ChangeDictionaryStatusRequestBody body) { return client.changeRejectionReasonStatus(o, reasonCode, body); }
    /** 完成当前路由授权后按配置归属分页查询原因的精确保存项引用，返回 BFF 本地模型。 */
    @GetMapping("/rejection-reasons/{reasonCode}/references")
    public PagingResponseBody<RejectionReasonReference> getRejectionReasonReferences(OperatorContext o, @PathVariable @Pattern(regexp = CODE) String reasonCode,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
        @RequestParam(required = false) @Pattern(regexp = "ALL|CURRENT|HISTORY|DELETED") String scope) {
        var p = PagingRequestBody.of(page, size); return client.getRejectionReasonReferences(o, reasonCode, p.page(), p.size(), scope);
    }
    /** 完成当前路由授权后按字面关键字和状态查询标签当前值分页，返回 BFF 本地模型。 */
    @GetMapping("/subject-tags")
    public PagingResponseBody<SubjectTagResponseBody> listSubjectTags(OperatorContext o, @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size, @RequestParam(required = false) @com.paytech.operations.console.bff.validation.DictionaryText(max = 100, nullable = true, trimmed = true) String keyword,
        @RequestParam(required = false) @Pattern(regexp = "ACTIVE|DISABLED") String status) {
        var p = PagingRequestBody.of(page, size); return client.listSubjectTags(o, p.page(), p.size(), keyword, status);
    }
    /** 完成当前路由授权后按不可变编码读取标签当前值，停用仍可查询，返回 BFF 本地模型。 */
    @GetMapping("/subject-tags/{tagCode}")
    public SubjectTagResponseBody getSubjectTag(OperatorContext o, @PathVariable @Pattern(regexp = CODE) String tagCode) { return client.getSubjectTag(o, tagCode); }
    /** 完成当前路由授权后创建并启用唯一的标签编码，返回 BFF 本地模型。 */
    @PostMapping("/subject-tags")
    public DictionaryWriteResponseBody createSubjectTag(OperatorContext o, @Valid @RequestBody CreateSubjectTagRequestBody body) { return client.createSubjectTag(o, body); }
    /** 完成当前路由授权后按期望版本完整更新标签内容，保留编码与状态，返回 BFF 本地模型。 */
    @PutMapping("/subject-tags/{tagCode}")
    public DictionaryWriteResponseBody updateSubjectTag(OperatorContext o, @PathVariable @Pattern(regexp = CODE) String tagCode, @Valid @RequestBody UpdateSubjectTagRequestBody body) { return client.updateSubjectTag(o, tagCode, body); }
    /** 完成当前路由授权后按期望版本独立启停标签，返回 BFF 本地模型。 */
    @PostMapping("/subject-tags/{tagCode}/status")
    public DictionaryWriteResponseBody changeSubjectTagStatus(OperatorContext o, @PathVariable @Pattern(regexp = CODE) String tagCode, @Valid @RequestBody ChangeDictionaryStatusRequestBody body) { return client.changeSubjectTagStatus(o, tagCode, body); }
    /** 完成当前路由授权后按配置归属分页查询标签的精确保存项引用，返回 BFF 本地模型。 */
    @GetMapping("/subject-tags/{tagCode}/references")
    public PagingResponseBody<DictionaryReference> getSubjectTagReferences(OperatorContext o, @PathVariable @Pattern(regexp = CODE) String tagCode,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
        @RequestParam(required = false) @Pattern(regexp = "ALL|CURRENT|HISTORY|DELETED") String scope) {
        var p = PagingRequestBody.of(page, size); return client.getSubjectTagReferences(o, tagCode, p.page(), p.size(), scope);
    }
}
