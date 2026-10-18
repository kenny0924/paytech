package com.paytech.platform.support.auto.ext.http.operations.notification;

import static com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome.DELIVERED;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.notificationmanagement.NotificationManagementApi;
import com.paytech.platform.support.api.notificationmanagement.request.ChangeNotificationStatusRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.CreateNotificationTemplateRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.PreviewNotificationContentRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.SaveNotificationContentRequestBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationMutationResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationPreviewResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationTemplateDetailResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationTemplateSummaryResponseBody;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;
import com.paytech.platform.support.test.base.NotificationManagementTestBase;
import com.paytech.platform.support.test.builder.NotificationRequestBuilder;
import com.paytech.platform.support.test.notification.NotificationFixture;

import tools.jackson.core.type.TypeReference;

/**
 * 通知模板与语言内容：列表、详情、新建、启停、保存内容（按发送规则校验）与预览；修改立即作用于之后的发送。
 *
 * @see NotificationManagementApi#templates(String, String, Integer, Integer, String, String)
 * @see NotificationManagementApi#template(String, String, String)
 * @see NotificationManagementApi#createTemplate(String, String, CreateNotificationTemplateRequestBody)
 * @see NotificationManagementApi#changeTemplateStatus(String, String, String, ChangeNotificationStatusRequestBody)
 * @see NotificationManagementApi#saveContent(String, String, String, String, SaveNotificationContentRequestBody)
 * @see NotificationManagementApi#preview(String, String, PreviewNotificationContentRequestBody)
 */
class NotificationTemplateManagementTest extends NotificationManagementTestBase {

    static final String NEW_TEMPLATE = "NOTIFICATION_ADMIN_ORDER_PAID";

    @Autowired SubmitNotificationProcessor submit;

    /** 按编码排序，带语言、用途与路由状态；关键字与状态可筛选。 */
    @Test
    void test_listTemplates() {
        var page = get(BASE + "/templates?page=1&size=10", reader).assertOk()
            .as(new TypeReference<PagingResponseBody<NotificationTemplateSummaryResponseBody>>() {});

        assertEquals(3, page.total());
        assertEquals(List.of("CLIENT_EMAIL_VERIFICATION", "CLIENT_PASSWORD_RESET", "NOTIFICATION_TEST"),
            page.items().stream().map(NotificationTemplateSummaryResponseBody::getTemplateCode).toList());
        var ordinary = page.items().get(2);
        assertAll(() -> assertTrue(page.items().getFirst().isTransientTemplate()),
            () -> assertFalse(ordinary.isTransientTemplate()),
            () -> assertEquals(List.of("en-US", "zh-CN"), ordinary.getLocales()),
            () -> assertEquals("ACTIVE", ordinary.getRouteStatus()), () -> assertEquals("EMAIL", ordinary.getMedium()));
        assertEquals(1, get(BASE + "/templates?keyword=RESET", reader).assertOk()
            .as(new TypeReference<PagingResponseBody<NotificationTemplateSummaryResponseBody>>() {}).total());
        assertEquals(0, get(BASE + "/templates?status=DISABLED", reader).assertOk()
            .as(new TypeReference<PagingResponseBody<NotificationTemplateSummaryResponseBody>>() {}).total());
        get(BASE + "/templates?status=PAUSED", reader).assertProblem(400, "INVALID_REQUEST");
    }

    /** 认证模板详情含必需参数 link、语言内容与路由；不存在的模板返回 404。 */
    @Test
    void test_templateDetail() {
        var detail = get(BASE + "/templates/CLIENT_PASSWORD_RESET", reader).assertOk()
            .as(NotificationTemplateDetailResponseBody.class);

        assertAll(() -> assertTrue(detail.isTransientTemplate()),
            () -> assertEquals(List.of("link"), detail.getRequiredParameters()),
            () -> assertEquals(1, detail.getContents().size()),
            () -> assertEquals(NotificationFixture.RESET_BODY, detail.getContents().getFirst().getBodyTemplate()),
            () -> assertEquals("NOTIFICATION_TEST_SES", detail.getRoute().getPrimaryProviderCode()),
            () -> assertNull(detail.getRoute().getFallbackProviderCode()));
        get(BASE + "/templates/NOTIFICATION_ADMIN_MISSING", reader).assertProblem(404, "NOT_FOUND");
    }

    /** 新建为停用；没有语言内容不能启用，加内容后启用；编码重复拒绝；每次写入记审计。 */
    @Test
    void test_createAndEnableTemplate() {
        var created = post(BASE + "/templates", admin, Map.of("templateCode", NEW_TEMPLATE, "reason", "新增付款通知"))
            .assertOk().as(NotificationMutationResponseBody.class);
        assertEquals(1, created.getVersion());
        assertEquals("DISABLED", mapper.templateState(NEW_TEMPLATE).get("status"));
        auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_TEMPLATE_CREATE", "SUCCEEDED", admin.actor(),
            "NOTIFICATION_TEMPLATE", NEW_TEMPLATE);
        assertNull(get(BASE + "/templates/" + NEW_TEMPLATE, reader).assertOk()
            .as(NotificationTemplateDetailResponseBody.class).getRoute());

        patch(BASE + "/templates/" + NEW_TEMPLATE + "/status", admin, status("ACTIVE", 1))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        put(BASE + "/templates/" + NEW_TEMPLATE + "/contents/zh-CN", admin,
            content("付款成功", "订单 ${orderNo} 已付款。", 0)).assertOk();
        var enabled = patch(BASE + "/templates/" + NEW_TEMPLATE + "/status", admin, status("ACTIVE", 1))
            .assertOk().as(NotificationMutationResponseBody.class);

        assertEquals(2, enabled.getVersion());
        assertEquals("ACTIVE", mapper.templateState(NEW_TEMPLATE).get("status"));
        auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_TEMPLATE_STATUS_CHANGE", "SUCCEEDED",
            admin.actor(), "NOTIFICATION_TEMPLATE", NEW_TEMPLATE);
        post(BASE + "/templates", admin, Map.of("templateCode", NEW_TEMPLATE, "reason", "重复"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        patch(BASE + "/templates/" + NEW_TEMPLATE + "/status", admin, status("ACTIVE", 2))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        patch(BASE + "/templates/" + NEW_TEMPLATE + "/status", admin, status("DISABLED", 1))
            .assertProblem(409, "CONCURRENT_REQUEST");
        patch(BASE + "/templates/" + NEW_TEMPLATE + "/status", admin, status("DISABLED", 2)).assertOk();
        assertEquals("DISABLED", mapper.templateState(NEW_TEMPLATE).get("status"));
    }

    /** 修改中文标题后，下一封普通通知即使用新标题；审计只记摘要，不含文案。 */
    @Test
    void test_saveContentTakesEffect() {
        var saved = put(BASE + "/templates/NOTIFICATION_TEST/contents/zh-CN", admin,
            content("新通知 ${name}", "${notice}：${name}", 1)).assertOk().as(NotificationMutationResponseBody.class);
        assertEquals(2, saved.getVersion());
        var event = auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_CONTENT_SAVE", "SUCCEEDED",
            admin.actor(), "NOTIFICATION_CONTENT", "NOTIFICATION_TEST:zh-CN");
        auditChecker.assertNoSecret(event, "新通知");

        vault.allow("alice@example.com", "vlt_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        provider.expectSend("alice@example.com", DELIVERED);
        submit.process(NotificationRequestBuilder.template());
        fixture.awaitIdle();

        assertEquals("新通知 Alice", provider.calls().getFirst().getSubject());
    }

    /** 不符合发送规则返回 422 并定位字段；版本冲突 409；语言或模板不合法分别 400、404；均不改数据。 */
    @Test
    void test_contentRejected() {
        String path = BASE + "/templates/CLIENT_PASSWORD_RESET/contents/zh-CN";
        put(path, admin, content("重置密码", "请联系客服", 1)).assertProblem(422, "VALIDATION_ERROR")
            .assertFieldError("bodyTemplate");
        put(path, admin, content("重置\n密码", "${link}", 1)).assertProblem(422, "VALIDATION_ERROR")
            .assertFieldError("subjectTemplate");
        put(path, admin, content("重置密码", "${1link} ${link}", 1)).assertProblem(422, "VALIDATION_ERROR")
            .assertFieldError("bodyTemplate");
        put(path, admin, content("重置密码", "${link}", 2)).assertProblem(409, "CONCURRENT_REQUEST");
        put(path, admin, content("重置密码", "${link}", 0)).assertProblem(409, "CONCURRENT_REQUEST");
        put(BASE + "/templates/CLIENT_PASSWORD_RESET/contents/zz-12345678901", admin, content("重置", "${link}", 0))
            .assertProblem(400, "INVALID_REQUEST");
        put(BASE + "/templates/NOTIFICATION_ADMIN_MISSING/contents/zh-CN", admin, content("a", "b", 0))
            .assertProblem(404, "NOT_FOUND");
        put(BASE + "/templates/CLIENT_PASSWORD_RESET/contents/fr-FR", admin, content("重置", "${link}", 1))
            .assertProblem(404, "NOT_FOUND");

        var row = mapper.contentState("CLIENT_PASSWORD_RESET", "zh-CN");
        assertEquals(NotificationFixture.RESET_BODY, row.get("body_template"));
        assertEquals(1, ((Number) row.get("version")).intValue());
    }

    /** 预览替换已提供的参数并列出缺失项；只读人员不能预览，运营只读人员看不到通知数据。 */
    @Test
    void test_preview() {
        var body = new HashMap<String, Object>(Map.of("subjectTemplate", "退款 ${refundNo}",
            "bodyTemplate", "您的退款 ${refundNo} 金额 ${amount}", "parameters", Map.of("refundNo", "R-1")));

        var preview = post(BASE + "/template-preview", admin, body).assertOk()
            .as(NotificationPreviewResponseBody.class);

        assertAll(() -> assertEquals("退款 R-1", preview.getSubject()),
            () -> assertEquals("您的退款 R-1 金额 ${amount}", preview.getBody()),
            () -> assertEquals(List.of("amount"), preview.getMissingParameters()));
        assertEquals(List.of("name"), post(BASE + "/template-preview", admin,
            Map.of("subjectTemplate", "通知 ${name}", "bodyTemplate", "正文")).assertOk()
            .as(NotificationPreviewResponseBody.class).getMissingParameters());
        post(BASE + "/template-preview", reader, body).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        post(BASE + "/templates", reader, Map.of("templateCode", NEW_TEMPLATE, "reason", "无权"))
            .assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        get(BASE + "/templates", viewer).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        assertNull(mapper.templateState(NEW_TEMPLATE));
    }

    /** 保存内容请求体。 */
    static Map<String, Object> content(String subject, String body, int expectedVersion) {
        return Map.of("subjectTemplate", subject, "bodyTemplate", body, "expectedVersion", expectedVersion,
            "reason", "测试修改");
    }
}
