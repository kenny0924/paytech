package com.paytech.client.console.bff.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.response.ApplicationResponseBody;
import com.paytech.client.console.bff.service.model.response.StartResponseBody;
import com.paytech.client.console.bff.service.model.response.FormOptionsResponseBody;
import com.paytech.client.console.bff.service.processor.ClientApplicationDetailsProcessor;
import com.paytech.client.console.bff.service.processor.ClientFormOptionsProcessor;
import com.paytech.client.console.bff.service.processor.ClientOnboardingStartProcessor;
import com.paytech.client.console.bff.service.processor.request.ClientApplicationDetailsRequest;
import com.paytech.client.console.bff.service.processor.request.ClientFormOptionsRequest;
import com.paytech.client.contract.api.onboarding.ClientDocumentApi;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentCommandReceiptResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentReferenceRequestBody;
import com.paytech.client.contract.api.onboarding.ClientDraftApi;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.CommandReceiptResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartApplicationRequestBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleApi;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.CreatePersonRequestBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.CreateRelationRequestBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PeopleCommandReceiptResponseBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.RelationPatchRequestBody;
import com.paytech.client.contract.api.onboarding.ClientSubmissionApi;
import com.paytech.client.contract.api.onboarding.response.SubmissionCheckResponseBody;
import com.paytech.client.contract.api.onboarding.response.SubmissionResponseBody;

/** 入驻路径的薄适配：三个页面查询交给 Processor 聚合，写命令只直接转发 CCT，不协调跨服务业务。 */
@RestController
@lombok.AllArgsConstructor
@RequestMapping("/client-onboarding/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingController {

    private final ClientDraftApi drafts;
    private final ClientPeopleApi people;
    private final ClientDocumentApi documents;
    private final ClientSubmissionApi submissions;
    private final ClientFormOptionsProcessor formOptions;
    private final ClientOnboardingStartProcessor startPage;
    private final ClientApplicationDetailsProcessor applicationDetails;

    /** 当前账号的原申请导航。 */
    @GetMapping("/start")
    public StartResponseBody start(ClientActor actor) {
        return startPage.process(actor);
    }

    /** 通用目录不接受任意主体或审核类型。 */
    @GetMapping("/form-options")
    public FormOptionsResponseBody options(
        ClientActor actor,
        @RequestParam String clientType
    ) {
        return formOptions.process(new ClientFormOptionsRequest(actor, clientType));
    }

    /** 创建及恢复保持 CCT 的 201/200。 */
    @PostMapping("/applications")
    public ResponseEntity<CommandReceiptResponseBody> create(
        ClientActor actor,
        @RequestBody StartApplicationRequestBody body
    ) {
        var result =
            drafts.create(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), body);
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    /** 当前表单、要求及文件状态由 BFF 组合。 */
    @GetMapping("/applications/{app}")
    public ApplicationResponseBody read(ClientActor actor, @PathVariable String app) {
        return applicationDetails.process(new ClientApplicationDetailsRequest(actor, app));
    }

    /** 页面每次只提交明确的脏字段。 */
    @PatchMapping("/applications/{app}/draft")
    public CommandReceiptResponseBody save(
        ClientActor actor,
        @PathVariable String app,
        @RequestBody DraftPatchRequestBody body
    ) {
        return drafts.save(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), app,
            body);
    }

    /** 个人资料及首条关系由 CCT 原子创建。 */
    @PostMapping("/applications/{app}/people")
    public ResponseEntity<PeopleCommandReceiptResponseBody> person(
        ClientActor actor,
        @PathVariable String app,
        @RequestBody CreatePersonRequestBody body
    ) {
        var result = people.createPerson(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(),
            actor.getSessionId(), app, body);
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    /** 共享批准人员的只读保护仍由 CCT 执行。 */
    @PatchMapping("/applications/{app}/people/{party}")
    public PeopleCommandReceiptResponseBody person(
        ClientActor actor,
        @PathVariable String app,
        @PathVariable String party,
        @RequestBody DraftPatchRequestBody body
    ) {
        return people.savePerson(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), app,
            party, body);
    }

    /** 同一人员追加关系，不创建另一个账号。 */
    @PostMapping("/applications/{app}/relations")
    public ResponseEntity<PeopleCommandReceiptResponseBody> relation(
        ClientActor actor,
        @PathVariable String app,
        @RequestBody CreateRelationRequestBody body
    ) {
        var result = people.createRelation(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(),
            actor.getSessionId(), app, body);
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    /** 合并原关系的明确修改字段。 */
    @PatchMapping("/applications/{app}/relations/{relation}")
    public PeopleCommandReceiptResponseBody relation(
        ClientActor actor,
        @PathVariable String app,
        @PathVariable String relation,
        @RequestBody RelationPatchRequestBody body
    ) {
        return people.saveRelation(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(),
            app, relation, body);
    }

    /** 删除只移除原申请关系，响应保持 204。 */
    @DeleteMapping("/applications/{app}/relations/{relation}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void removeRelation(
        ClientActor actor,
        @PathVariable String app,
        @PathVariable String relation
    ) {
        people.removeRelation(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), app,
            relation, null);
    }

    /** 材料关联的当前要求及文件证明由 CCT 核验。 */
    @PostMapping("/applications/{app}/documents")
    public ResponseEntity<DocumentCommandReceiptResponseBody> associate(
        ClientActor actor,
        @PathVariable String app,
        @RequestBody DocumentReferenceRequestBody body
    ) {
        var result = documents.associate(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(),
            actor.getSessionId(), app, body);
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    /** C14 正式提交；Body 为空，重复提交由 CCT 按申请状态返回原提交。 */
    @PostMapping("/applications/{app}/submissions")
    public ResponseEntity<SubmissionResponseBody> submit(ClientActor actor, @PathVariable String app) {
        var result =
            submissions.submit(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), app);
        return ResponseEntity.status(result.getStatusCode()).cacheControl(CacheControl.noStore()).body(result.getBody());
    }

    /** C16 提交前核对：转发 CCT 的逐项问题，不改变状态。 */
    @GetMapping("/applications/{app}/submission-check")
    public ResponseEntity<SubmissionCheckResponseBody> check(ClientActor actor, @PathVariable String app) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(submissions.check(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(),
                app));
    }

    /** 删除引用不调用 Platform 删除文件。 */
    @DeleteMapping("/applications/{app}/documents/{document}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void removeDocument(
        ClientActor actor,
        @PathVariable String app,
        @PathVariable String document
    ) {
        documents.remove(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), app,
            document);
    }
}
