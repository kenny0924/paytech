package com.paytech.operations.console.bff.ext.rest;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformNotificationManagementClient;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.ChangeNotificationStatusRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.CreateNotificationProviderRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.CreateNotificationTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationPreviewResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationProviderSummary;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationProviderTestSendResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationRequestDetailResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationRequestSummary;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationTemplateDetailResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationTemplateSummary;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.PreviewNotificationContentRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.ReplaceNotificationCredentialRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.SaveNotificationContentRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.SaveNotificationRouteRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.TestSendNotificationProviderRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.UpdateNotificationProviderRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 通知管理的浏览器入口，全部简单转发；沿用公共 Origin、PLT-I02 与可信身份边界。 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/notification-admin/v1")
public class NotificationManagementController {

    private final PlatformNotificationManagementClient client;

    /** 模板分页查询。 */
    @GetMapping("/templates")
    public PagingResponseBody<NotificationTemplateSummary> templates(OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "status", required = false) String status) {
        return client.templates(context, page, size, keyword, status);
    }

    /** 模板详情。 */
    @GetMapping("/templates/{templateCode}")
    public NotificationTemplateDetailResponseBody template(OperatorContext context,
        @PathVariable("templateCode") String templateCode) {
        return client.template(context, templateCode);
    }

    /** 新建模板。 */
    @PostMapping("/templates")
    public NotificationMutationResponseBody createTemplate(OperatorContext context,
        @Valid @RequestBody CreateNotificationTemplateRequestBody request) {
        return client.createTemplate(context, request);
    }

    /** 启停模板。 */
    @PatchMapping("/templates/{templateCode}/status")
    public NotificationMutationResponseBody changeTemplateStatus(OperatorContext context,
        @PathVariable("templateCode") String templateCode,
        @Valid @RequestBody ChangeNotificationStatusRequestBody request) {
        return client.changeTemplateStatus(context, templateCode, request);
    }

    /** 保存语言内容。 */
    @PutMapping("/templates/{templateCode}/contents/{locale}")
    public NotificationMutationResponseBody saveContent(OperatorContext context,
        @PathVariable("templateCode") String templateCode, @PathVariable("locale") String locale,
        @Valid @RequestBody SaveNotificationContentRequestBody request) {
        return client.saveContent(context, templateCode, locale, request);
    }

    /** 预览内容草稿。 */
    @PostMapping("/template-preview")
    public NotificationPreviewResponseBody preview(OperatorContext context,
        @Valid @RequestBody PreviewNotificationContentRequestBody request) {
        return client.preview(context, request);
    }

    /** 保存路由。 */
    @PutMapping("/templates/{templateCode}/route")
    public NotificationMutationResponseBody saveRoute(OperatorContext context,
        @PathVariable("templateCode") String templateCode,
        @Valid @RequestBody SaveNotificationRouteRequestBody request) {
        return client.saveRoute(context, templateCode, request);
    }

    /** 启停路由。 */
    @PatchMapping("/templates/{templateCode}/route/status")
    public NotificationMutationResponseBody changeRouteStatus(OperatorContext context,
        @PathVariable("templateCode") String templateCode,
        @Valid @RequestBody ChangeNotificationStatusRequestBody request) {
        return client.changeRouteStatus(context, templateCode, request);
    }

    /** 发送账号分页查询。 */
    @GetMapping("/providers")
    public PagingResponseBody<NotificationProviderSummary> providers(OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.providers(context, page, size);
    }

    /** 启停发送账号。 */
    @PatchMapping("/providers/{providerCode}/status")
    public NotificationMutationResponseBody changeProviderStatus(OperatorContext context,
        @PathVariable("providerCode") String providerCode,
        @Valid @RequestBody ChangeNotificationStatusRequestBody request) {
        return client.changeProviderStatus(context, providerCode, request);
    }

    /** 新建发送账号。 */
    @PostMapping("/providers")
    public NotificationMutationResponseBody createProvider(OperatorContext context,
        @Valid @RequestBody CreateNotificationProviderRequestBody request) {
        return client.createProvider(context, request);
    }

    /** 修改发送账号。 */
    @PutMapping("/providers/{providerCode}")
    public NotificationMutationResponseBody updateProvider(OperatorContext context,
        @PathVariable("providerCode") String providerCode,
        @Valid @RequestBody UpdateNotificationProviderRequestBody request) {
        return client.updateProvider(context, providerCode, request);
    }

    /** 录入或更换发送账号密钥。 */
    @PutMapping("/providers/{providerCode}/credential")
    public NotificationMutationResponseBody replaceProviderCredential(OperatorContext context,
        @PathVariable("providerCode") String providerCode,
        @Valid @RequestBody ReplaceNotificationCredentialRequestBody request) {
        return client.replaceProviderCredential(context, providerCode, request);
    }

    /** 发送账号测试发送。 */
    @PostMapping("/providers/{providerCode}/test-send")
    public NotificationProviderTestSendResponseBody testSendProvider(OperatorContext context,
        @PathVariable("providerCode") String providerCode,
        @Valid @RequestBody TestSendNotificationProviderRequestBody request) {
        return client.testSendProvider(context, providerCode, request);
    }

    /** 发送记录分页查询。 */
    @GetMapping("/requests")
    public PagingResponseBody<NotificationRequestSummary> requests(OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "createdFrom", required = false) String createdFrom,
        @RequestParam(value = "createdTo", required = false) String createdTo,
        @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
        @RequestParam(value = "sourceEventKey", required = false) String sourceEventKey,
        @RequestParam(value = "templateCode", required = false) String templateCode,
        @RequestParam(value = "deliveryStatus", required = false) String deliveryStatus) {
        return client.requests(context, page, size, createdFrom, createdTo, sourceSystem, sourceEventKey,
            templateCode, deliveryStatus);
    }

    /** 发送记录详情。 */
    @GetMapping("/requests/{requestId}")
    public NotificationRequestDetailResponseBody request(OperatorContext context,
        @PathVariable("requestId") String requestId) {
        return client.request(context, requestId);
    }
}
