package com.paytech.client.console.bff.configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
/** 错误体由 Foundation 输出，本地只声明状态映射。 */
@Component
public class ClientErrorStatusResolver implements ApiErrorStatusResolver {
    @Override public HttpStatus resolve(CodeMessageEnum code,HttpStatus fallback) {
        if(code instanceof ClientAuthenticationErrorCode value) {
            return switch(value) {
                case CLIENT_EMAIL_LINK_INVALID -> HttpStatus.UNPROCESSABLE_CONTENT;
                case CLIENT_WORKSPACE_UNAVAILABLE, CLIENT_PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.UNAUTHORIZED;
            };
        }
        if(code instanceof ClientBffErrorCode value) {
            return switch(value) {
                case ORIGIN_REJECTED, WORKSPACE_CONTEXT_REQUIRED, ACCOUNT_CONTEXT_REQUIRED -> HttpStatus.FORBIDDEN;
                case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                case DEPENDENCY_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            };
        }
        if(code instanceof ClientTeamErrorCode value) {
            // 与 Platform 团队与邀请合同一致；链接无效不区分原因，统一 404。
            return switch(value) {
                case CLIENT_INVITATION_INVALID -> HttpStatus.NOT_FOUND;
                case CLIENT_INVITATION_EMAIL_MISMATCH -> HttpStatus.FORBIDDEN;
                case CLIENT_INVITATION_CLOSED, CLIENT_INVITATION_DUPLICATE, CLIENT_INVITATION_LIMIT_REACHED,
                    CLIENT_MEMBER_EXISTS, CLIENT_MEMBER_PROTECTED -> HttpStatus.CONFLICT;
            };
        }
        if(code instanceof com.paytech.client.contract.api.enums.ClientDraftErrorCode) {
            return switch(code.getCode()) {
                case "CLIENT_SESSION_INVALID" -> HttpStatus.UNAUTHORIZED;
                case "INVALID_DRAFT","ORGANIZATION_REQUIRED","MATERIAL_REQUIREMENT_INVALID","SUBMISSION_INCOMPLETE",
                    "DECLARATION_DOCUMENT_OUTDATED" -> HttpStatus.UNPROCESSABLE_CONTENT;
                case "DOCUMENT_NOT_FOUND","APPLICATION_NOT_FOUND","PERSON_NOT_FOUND","RELATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                case "DRAFT_CONTEXT_INVALID","ONBOARDING_DEPENDENCY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.CONFLICT;
            };
        }
        if(code instanceof com.paytech.client.master.api.enums.OnboardingErrorCode value) {
            return switch(value) {
                case ONBOARDING_CODE_INVALID, JURISDICTION_NOT_ACCEPTED, PARTNER_NOT_OE_PROVIDER -> HttpStatus.UNPROCESSABLE_CONTENT;
                case ONBOARDING_CODE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case APPLICATION_NOT_WITHDRAWABLE -> HttpStatus.CONFLICT;
            };
        }
        if(code instanceof com.paytech.platform.support.api.enums.FileErrorCode) {
            return switch(code.getCode()) {
                case "FILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                case "FILE_POLICY_REJECTED" -> HttpStatus.UNPROCESSABLE_CONTENT;
                default -> HttpStatus.CONFLICT;
            };
        }
        if(code instanceof com.paytech.payout.api.enums.PayoutErrorCode value) {
            // 与 payout 合同一致：账户不属于当前工作区与不存在同为 404。
            return switch(value) {
                case BENEFICIARY_DUPLICATE, BENEFICIARY_STATUS_CONFLICT -> HttpStatus.CONFLICT;
                case BENEFICIARY_UNSUPPORTED_ROUTE -> HttpStatus.UNPROCESSABLE_CONTENT;
                case FOUR_EYES_REQUIRED, BENEFICIARY_REVEAL_FORBIDDEN -> HttpStatus.FORBIDDEN;
                case BENEFICIARY_ACCOUNT_NOT_FOUND, BENEFICIARY_REQUIREMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            };
        }
        if(code==com.paytech.foundation.common.error.CommonErrorCode.NOT_FOUND) {
            // Platform 对其他工作区或不存在的凭证、端点、投递、成员、邀请统一返回 NOT_FOUND，浏览器保持 404。
            return HttpStatus.NOT_FOUND;
        }
        return fallback;
    }
}
