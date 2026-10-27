package com.paytech.client.console.bff.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.util.Set;

import org.springframework.web.bind.annotation.*;

import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.client.master.api.onboarding.ClientOnboardingApplicationApi;
import com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi;
import com.paytech.client.master.api.onboarding.request.DeclarationRequestBody;
import com.paytech.client.master.api.onboarding.response.DeclarationResponseBody;
import com.paytech.client.master.api.onboarding.response.JurisdictionResponseBody;
import com.paytech.client.master.api.onboarding.response.RequirementsResponseBody;
import com.paytech.client.master.api.onboarding.response.WithdrawalResponseBody;
import com.paytech.client.contract.api.onboarding.response.SubmissionCheckResponseBody;
import java.util.List;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.response.ApplicationResponseBody;
import com.paytech.client.console.bff.service.model.response.ClientFormSchemaResponseBody;
import com.paytech.client.console.bff.service.model.response.StartResponseBody;
import com.paytech.client.console.bff.service.processor.ClientApplicationDetailsProcessor;
import com.paytech.client.console.bff.service.processor.ClientOnboardingStartProcessor;
import com.paytech.client.console.bff.service.processor.GetClientFormSchemaProcessor;
import com.paytech.client.console.bff.service.processor.request.ClientApplicationDetailsRequest;
import com.paytech.client.console.bff.service.processor.request.GetClientFormSchemaRequest;
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
import com.paytech.client.contract.api.onboarding.response.SubmissionResponseBody;

/** 入驻路径的薄适配：开始页、申请详情与采集 Schema 三个页面查询交给 Processor 聚合，写命令只直接转发 CCT，不协调跨服务业务。 */
@RestController
@lombok.AllArgsConstructor
@RequestMapping("/client-onboarding/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingController {

    private final ClientDraftApi drafts;
    private final ClientPeopleApi people;
    private final ClientDocumentApi documents;
    private final ClientSubmissionApi submissions;
    private final ClientOnboardingCatalogApi catalogs;
    private final ClientOnboardingApplicationApi onboardingApplications;
    private final ClientOnboardingStartProcessor startPage;
    private final ClientApplicationDetailsProcessor applicationDetails;
    private final GetClientFormSchemaProcessor formSchemaPage;

    /** 当前账号的原申请导航。 */
    @GetMapping("/start")
    public StartResponseBody start(ClientActor actor) {
        return startPage.process(actor);
    }

    /** CM-1 可受理辖区；只返回 ACTIVE 且未关闭的辖区。 */
    @GetMapping("/jurisdictions")
    public ResponseEntity<List<JurisdictionResponseBody>> jurisdictions(ClientActor actor) {
        var value = catalogs.jurisdictions(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId());
        return ok(requireShape(value, value != null));
    }

    /**
     * CM-2 采集 Schema
     * <li>有 applicationId 时 Owner 返回该申请冻结的版本与责任方材料（其余参数可省略，申请归属由 Owner 核对）；否则辖区与主体类型必填。</li>
     * <li>Query 已由拦截器限定为这四个单值参数。</li>
     * <li>Processor 补全 Platform 参考数据选项（国家、REF_ 目录）；参考数据读取失败时整页失败。</li>
     */
    @GetMapping("/form-schema")
    public ResponseEntity<ClientFormSchemaResponseBody> formSchema(
        ClientActor actor,
        @RequestParam(required = false) String applicationId,
        @RequestParam(required = false) String jurisdiction,
        @RequestParam(required = false) String partyType,
        @RequestParam(required = false) String organizationType
    ) {
        boolean byApplication = applicationId != null && applicationId.matches("[1-9][0-9]{0,18}");
        boolean byScope = jurisdiction != null && jurisdiction.matches(CODE) && partyType != null && PARTY_TYPES.contains(partyType);
        if ((applicationId != null && !byApplication) || (!byApplication && !byScope)
            || (jurisdiction != null && !jurisdiction.matches(CODE)) || (partyType != null && !PARTY_TYPES.contains(partyType))
            || (organizationType != null && !organizationType.matches(CODE))) {
            throw new InvalidParameterException("入驻查询参数无效");
        }
        return ok(formSchemaPage.process(
            new GetClientFormSchemaRequest(actor, applicationId, jurisdiction, partyType, organizationType)));
    }

    /** CM-6 逐条声明；只接受声明代码与 TRUE/FALSE，账号、时间、IP 与浏览器由 Owner 记录。 */
    @PostMapping("/applications/{app}/declarations")
    public ResponseEntity<DeclarationResponseBody> declare(
        ClientActor actor,
        @PathVariable String app,
        @RequestHeader(value = "User-Agent", required = false) String userAgent,
        @RequestHeader(value = "X-Gateway-Client-Ip", required = false) String clientIp,
        @RequestBody DeclarationRequestBody body
    ) {
        if (body == null || body.getDeclarationType() == null || !body.getDeclarationType().matches(CODE)
            || !Set.of("TRUE", "FALSE").contains(body.getDeclaredValue())
            || (body.getTermsVersion() != null && body.getTermsVersion().length() > 64)) {
            throw new InvalidParameterException("声明参数无效");
        }
        var value = onboardingApplications.declare(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(),
            clientIp, userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 512)), app, body);
        return ok(value);
    }

    /** CM-7 实时需求清单。 */
    @GetMapping("/applications/{app}/requirements")
    public ResponseEntity<RequirementsResponseBody> requirements(ClientActor actor, @PathVariable String app) {
        var value = onboardingApplications.requirements(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(),
            actor.getSessionId(), app);
        return ok(requireShape(value, value != null && value.getItems() != null));
    }

    /** CM-8 撤回；Body 为空，DRAFT / RETURNED 之外的状态由 Owner 返回 409。 */
    @PostMapping("/applications/{app}/withdrawal")
    public ResponseEntity<WithdrawalResponseBody> withdraw(ClientActor actor, @PathVariable String app) {
        return ok(onboardingApplications.withdraw(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), app));
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

    /** CM-9 提交前核对：`{ready, errors[]}` 原样转发，不改变状态。 */
    @GetMapping("/applications/{app}/submission-check")
    public ResponseEntity<SubmissionCheckResponseBody> check(ClientActor actor, @PathVariable String app) {
        var value = submissions.check(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), app);
        return ok(requireShape(value, value != null && value.getErrors() != null && value.isReady() == value.getErrors().isEmpty()));
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

    /** 主体类型合同值。 */
    static final Set<String> PARTY_TYPES = Set.of("INDIVIDUAL", "ORGANIZATION");

    /** 辖区、组织形式与声明类型都是大写代码。 */
    static final String CODE = "[A-Z][A-Z0-9_]{0,63}";

    /** Owner 回执形状不符时按依赖不可用，不向页面透传可疑内容。 */
    static <T> T requireShape(T value, boolean valid) {
        if (!valid) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 入驻查询结果不缓存。 */
    static <T> ResponseEntity<T> ok(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
