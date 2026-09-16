package com.paytech.client.console.bff.configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
/** 错误体由 Foundation 输出，本地只声明状态映射。 */
@Component
public class ClientErrorStatusResolver implements ApiErrorStatusResolver {
    @Override public HttpStatus resolve(CodeMessageEnum code,HttpStatus fallback) {
        if(code instanceof ClientAuthenticationErrorCode value) {
            return value==ClientAuthenticationErrorCode.CLIENT_EMAIL_LINK_INVALID?HttpStatus.UNPROCESSABLE_CONTENT:HttpStatus.UNAUTHORIZED;
        }
        if(code instanceof ClientBffErrorCode value) {
            return switch(value) {
                case ORIGIN_REJECTED -> HttpStatus.FORBIDDEN;
                case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                case DEPENDENCY_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            };
        }
        if(code instanceof com.paytech.client.contract.api.enums.ClientDraftErrorCode) {
            return switch(code.getCode()) {
                case "CLIENT_SESSION_INVALID" -> HttpStatus.UNAUTHORIZED;
                case "INVALID_DRAFT","ORGANIZATION_REQUIRED","MATERIAL_COUNTRY_REQUIRED","MATERIAL_REQUIREMENT_INVALID" -> HttpStatus.UNPROCESSABLE_CONTENT;
                case "DOCUMENT_NOT_FOUND","APPLICATION_NOT_FOUND","PERSON_NOT_FOUND","RELATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                case "DRAFT_CONTEXT_INVALID","ONBOARDING_DEPENDENCY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.CONFLICT;
            };
        }
        if(code instanceof com.paytech.platform.support.api.enums.OnboardingFileErrorCode) {
            return switch(code.getCode()) {
                case "FILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                case "FILE_POLICY_REJECTED" -> HttpStatus.UNPROCESSABLE_CONTENT;
                default -> HttpStatus.CONFLICT;
            };
        }
        return fallback;
    }
}
