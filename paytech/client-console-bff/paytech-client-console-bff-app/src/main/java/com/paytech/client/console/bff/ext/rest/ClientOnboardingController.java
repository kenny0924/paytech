package com.paytech.client.console.bff.ext.rest;

import com.paytech.client.console.bff.service.model.ClientOnboardingModels;
import com.paytech.client.console.bff.ext.rest.response.FormOptionsResponseBody;
import com.paytech.client.console.bff.service.processor.*;
import com.paytech.client.contract.api.onboarding.*;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.*;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import com.paytech.client.contract.api.onboarding.response.SubmissionCheckResponseBody;
import com.paytech.client.contract.api.onboarding.response.SubmissionResponseBody;
import org.springframework.web.bind.annotation.*;

/** 已批准 Client 路径的薄适配；写命令只交给 CCT，不协调跨服务业务。 */
@RestController
@lombok.AllArgsConstructor
@RequestMapping("/client-onboarding/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingController {

    private final ClientDraftApi drafts;
    private final ClientPeopleApi people;
    private final ClientDocumentApi documents;
    private final ClientSubmissionApi submissions;
    private final ClientSessionProcessor sessions;
    private final ClientOnboardingProcessor queries;

    /** 当前账号的原申请导航。 */
    @GetMapping("/start")
    public ClientOnboardingModels.StartResponseBody start(HttpServletRequest request) {
        return queries.start(request);
    }

    /** 通用目录不接受任意主体或审核类型。 */
    @GetMapping("/form-options")
    public FormOptionsResponseBody options(
        HttpServletRequest request,
        @RequestParam String clientType
    ) {
        return queries.options(request, clientType);
    }

    /** 创建及恢复保持 CCT 的 201/200。 */
    @PostMapping("/applications")
    public ResponseEntity<CommandReceiptResponseBody> create(
        HttpServletRequest request,
        @RequestBody StartApplicationRequestBody body
    ) {
        var actor = sessions.identity(request);
        var result = drafts.create("client-console-bff", actor[0], actor[1], body);
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    /** 当前表单、要求及文件状态由 BFF 组合。 */
    @GetMapping("/applications/{app}")
    public ClientOnboardingModels.ApplicationResponseBody read(HttpServletRequest request, @PathVariable String app) {
        return queries.read(request, app);
    }

    /** 页面每次只提交明确的脏字段。 */
    @PatchMapping("/applications/{app}/draft")
    public CommandReceiptResponseBody save(
        HttpServletRequest request,
        @PathVariable String app,
        @RequestBody DraftPatchRequestBody body
    ) {
        var actor = sessions.identity(request);
        return drafts.save("client-console-bff", actor[0], actor[1], app, body);
    }

    /** 个人资料及首条关系由 CCT 原子创建。 */
    @PostMapping("/applications/{app}/people")
    public ResponseEntity<PeopleCommandReceiptResponseBody> person(
        HttpServletRequest request,
        @PathVariable String app,
        @RequestBody CreatePersonRequestBody body
    ) {
        var actor = sessions.identity(request);
        var result = people.createPerson("client-console-bff", actor[0], actor[1], app, body);
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    /** 共享批准人员的只读保护仍由 CCT 执行。 */
    @PatchMapping("/applications/{app}/people/{party}")
    public PeopleCommandReceiptResponseBody person(
        HttpServletRequest request,
        @PathVariable String app,
        @PathVariable String party,
        @RequestBody DraftPatchRequestBody body
    ) {
        var actor = sessions.identity(request);
        return people.savePerson("client-console-bff", actor[0], actor[1], app, party, body);
    }

    /** 同一人员追加关系，不创建另一个账号。 */
    @PostMapping("/applications/{app}/relations")
    public ResponseEntity<PeopleCommandReceiptResponseBody> relation(
        HttpServletRequest request,
        @PathVariable String app,
        @RequestBody CreateRelationRequestBody body
    ) {
        var actor = sessions.identity(request);
        var result = people.createRelation("client-console-bff", actor[0], actor[1], app, body);
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    /** 合并原关系的明确修改字段。 */
    @PatchMapping("/applications/{app}/relations/{relation}")
    public PeopleCommandReceiptResponseBody relation(
        HttpServletRequest request,
        @PathVariable String app,
        @PathVariable String relation,
        @RequestBody RelationPatchRequestBody body
    ) {
        var actor = sessions.identity(request);
        return people.saveRelation("client-console-bff", actor[0], actor[1], app, relation, body);
    }

    /** 删除只移除原申请关系，响应保持 204。 */
    @DeleteMapping("/applications/{app}/relations/{relation}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void removeRelation(
        HttpServletRequest request,
        @PathVariable String app,
        @PathVariable String relation
    ) {
        var actor = sessions.identity(request);
        people.removeRelation("client-console-bff", actor[0], actor[1], app, relation, null);
    }

    /** 材料关联的当前要求及文件证明由 CCT 核验。 */
    @PostMapping("/applications/{app}/documents")
    public ResponseEntity<DocumentCommandReceiptResponseBody> associate(
        HttpServletRequest request,
        @PathVariable String app,
        @RequestBody DocumentReferenceRequestBody body
    ) {
        var actor = sessions.identity(request);
        var result = documents.associate("client-console-bff", actor[0], actor[1], app, body);
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    /** C14 正式提交；Body 为空，重复提交由 CCT 按申请状态返回原提交。 */
    @PostMapping("/applications/{app}/submissions")
    public ResponseEntity<SubmissionResponseBody> submit(HttpServletRequest request, @PathVariable String app) {
        var actor = sessions.identity(request);
        var result = submissions.submit("client-console-bff", actor[0], actor[1], app);
        return ResponseEntity.status(result.getStatusCode()).cacheControl(CacheControl.noStore()).body(result.getBody());
    }

    /** C16 提交前核对：转发 CCT 的逐项问题，不改变状态。 */
    @GetMapping("/applications/{app}/submission-check")
    public ResponseEntity<SubmissionCheckResponseBody> check(HttpServletRequest request, @PathVariable String app) {
        var actor = sessions.identity(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(submissions.check("client-console-bff", actor[0], actor[1], app));
    }

    /** 删除引用不调用 Platform 删除文件。 */
    @DeleteMapping("/applications/{app}/documents/{document}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void removeDocument(
        HttpServletRequest request,
        @PathVariable String app,
        @PathVariable String document
    ) {
        var actor = sessions.identity(request);
        documents.remove("client-console-bff", actor[0], actor[1], app, document);
    }
}
