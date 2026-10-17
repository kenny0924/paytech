package com.paytech.client.console.bff.ext.rest;

import com.paytech.client.console.bff.service.model.ClientInvitationDetailsResponseBody;
import com.paytech.client.console.bff.service.processor.ClientInvitationProcessor;
import com.paytech.client.console.bff.service.processor.ClientSessionProcessor;
import com.paytech.platform.support.api.clientinvitation.ClientInvitationApi;
import com.paytech.platform.support.api.clientinvitation.request.ClientInvitationProofRequestBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationAcceptanceResponseBody;
import com.paytech.platform.support.api.clientinvitation.response.ClientInvitationPreviewResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 被邀请人打开邀请链接后的接口
 * <li>预览公开（不需要身份），详情与接受要求账号上下文；边界由 {@code ClientInvitationWebConfiguration} 先行判断。</li>
 * <li>预览与接受直接透传 Platform；详情由 Processor 补充 CCT 工作区名称。请求含链接 Secret，日志按 metadata-only 前缀处理。</li>
 */
@RestController
@lombok.AllArgsConstructor
@RequestMapping("/client-invitations/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientInvitationController {

    private final ClientInvitationApi invitations;
    private final ClientSessionProcessor sessions;
    private final ClientInvitationProcessor details;

    /** I01 未登录预览：脱敏邮箱、角色、状态与到期时间，不含工作区信息。 */
    @PostMapping("/preview")
    public ClientInvitationPreviewResponseBody preview(@Valid @RequestBody ClientInvitationProofRequestBody body) {
        return invitations.preview(body);
    }

    /** I02 已登录详情：附邮箱是否一致，一致时附工作区名称。 */
    @PostMapping("/details")
    public ClientInvitationDetailsResponseBody details(HttpServletRequest request,
        @Valid @RequestBody ClientInvitationProofRequestBody body) {
        return details.details(sessions.identity(request), body);
    }

    /** I03 接受邀请：JOINED 或 ALREADY_MEMBER。 */
    @PostMapping("/acceptance")
    public ClientInvitationAcceptanceResponseBody accept(HttpServletRequest request,
        @Valid @RequestBody ClientInvitationProofRequestBody body) {
        var id = sessions.identity(request);
        return invitations.accept(id[0], id[1], body);
    }
}
