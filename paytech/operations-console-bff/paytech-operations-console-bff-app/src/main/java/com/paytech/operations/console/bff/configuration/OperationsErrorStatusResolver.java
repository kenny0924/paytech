package com.paytech.operations.console.bff.configuration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.platform.support.api.enums.GatewayConfigurationErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode;

/** 仅声明浏览器错误状态；错误体、Request ID 和异常终态处理统一归 Foundation。 @author Codex */
@Component
public final class OperationsErrorStatusResolver implements ApiErrorStatusResolver {

    @Override
    public HttpStatus resolve(CodeMessageEnum errorCode, HttpStatus defaultStatus) {
        if (errorCode == com.paytech.foundation.common.error.CommonErrorCode.NOT_FOUND) return HttpStatus.NOT_FOUND;
        if (errorCode instanceof com.paytech.risk.decision.api.enums.RiskVariableErrorCode variable) {
            return switch (variable) {
                case INVALID_VARIABLE_DEFINITION, INVALID_OPERAND, LEGACY_AUTHORING_NOT_SUPPORTED -> HttpStatus.BAD_REQUEST;
                case SYSTEM_VARIABLE_READ_ONLY -> HttpStatus.FORBIDDEN;
                case VARIABLE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                default -> HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof com.paytech.vault.client.VaultClientErrorCode vault) {
            return switch (vault) {
                case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
                case TOKEN_NOT_FOUND, POLICY_ENTRY_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case IDEMPOTENCY_KEY_CONFLICT, POLICY_VERSION_CONFLICT, POLICY_ENTRY_EXISTS -> HttpStatus.CONFLICT;
                default -> defaultStatus;
            };
        }
        if (errorCode instanceof OperationsBffErrorCode browserCode) {
            return switch (browserCode) {
                case ORIGIN_REJECTED -> HttpStatus.FORBIDDEN;
                case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                case DEPENDENCY_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                case QUALIFICATION_SUBJECT_TERMINATED -> HttpStatus.UNPROCESSABLE_CONTENT;
            };
        }
        if (errorCode instanceof OperationsErrorCode platformCode) {
            return switch (platformCode) {
                case OPERATIONS_AUTHENTICATION_FAILED, OPERATIONS_SESSION_INVALID -> HttpStatus.UNAUTHORIZED;
                case OPERATIONS_PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            };
        }
        if (errorCode instanceof com.paytech.platform.support.api.enums.DataQueryErrorCode dataQuery) {
            return switch (dataQuery) {
                case DATA_QUERY_TIMEOUT, DATA_QUERY_RESULT_TOO_LARGE -> HttpStatus.BAD_REQUEST;
                case DATA_SOURCE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            };
        }
        if (errorCode instanceof com.paytech.platform.support.api.enums.LocalizationErrorCode localization) {
            return switch (localization) {
                case I18N_COVERAGE_INSUFFICIENT, I18N_NAMESPACE_NOT_FOUND, I18N_MESSAGE_INVALID -> HttpStatus.UNPROCESSABLE_CONTENT;
                case I18N_IMPORT_PREVIEW_STALE, I18N_CONTENT_CHANGED, I18N_IMPORT_FILE_NOT_READY -> HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof GatewayConfigurationErrorCode gatewayCode) {
            return switch (gatewayCode) {
                case GATEWAY_CONFIGURATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            };
        }
        if (errorCode instanceof RiskDecisionConfigurationErrorCode riskCode) {
            return switch (riskCode) {
                case RISK_DICTIONARY_NOT_FOUND, RISK_DECISION_CONFIGURATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case RISK_FLOW_REFERENCE_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
                case RISK_FLOW_CODE_EXISTS, RISK_FLOW_VERSION_CONFLICT, RISK_FLOW_DELETED, RISK_DICTIONARY_CODE_EXISTS, RISK_DICTIONARY_CHANGED, RISK_DICTIONARY_SELECTION_UNAVAILABLE, RISK_RULE_SET_IN_USE, RISK_RULE_SET_CHANGED, RISK_RULE_SET_DELETED -> HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof com.paytech.compliance.api.enums.ClientReviewErrorCode review) {
            return switch (review) {
                case CLIENT_REVIEW_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CLIENT_REVIEW_NOT_READY -> HttpStatus.UNPROCESSABLE_CONTENT;
                default -> HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof com.paytech.platform.support.api.enums.FileErrorCode file) {
            return switch (file) {
                case FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case FILE_STATE_CONFLICT -> HttpStatus.CONFLICT;
                case FILE_POLICY_REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
            };
        }
        if (errorCode instanceof com.paytech.commercial.api.enums.CommercialPriceErrorCode price) {
            return switch (price) {
                case PRICE_AMBIGUOUS -> HttpStatus.CONFLICT;
                case PRICING_FACTS_INCOMPLETE, PRICE_NOT_FOUND, INVALID_PRICING_FACT -> HttpStatus.UNPROCESSABLE_CONTENT;
            };
        }
        // channel-connector 以 400 返回成本命中拒绝；显式固定为 400，避免未来默认状态变化改变浏览器合同。
        if (errorCode instanceof com.paytech.channel.connector.api.enums.ChannelCostErrorCode channelCost) {
            return switch (channelCost) {
                case CHANNEL_COST_NOT_RESOLVED, CHANNEL_COST_AMBIGUOUS -> HttpStatus.BAD_REQUEST;
            };
        }
        // reconciliation 按主键读取的详情页对象不存在为 404；清算账户未解析保持默认 400。
        if (errorCode instanceof com.paytech.reconciliation.api.enums.ReconciliationErrorCode reconciliation) {
            return switch (reconciliation) {
                case BANK_STATEMENT_NOT_FOUND, BANK_STATEMENT_LINE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CLEARING_ACCOUNT_NOT_RESOLVED -> defaultStatus;
            };
        }
        // 主档维护与资格登记（2026-10-23 商业关系快照）：Owner 404 / 409 / 422 原样到达浏览器。
        if (errorCode instanceof com.paytech.client.master.api.enums.EntityAdminErrorCode entityAdmin) {
            return switch (entityAdmin) {
                case OPERATING_ENTITY_NOT_FOUND, LOCAL_PARTNER_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case ENTITY_CHANGE_VERSION_CONFLICT -> HttpStatus.CONFLICT;
                case ENTITY_ATTRIBUTE_INVALID -> HttpStatus.UNPROCESSABLE_CONTENT;
            };
        }
        if (errorCode == com.paytech.client.master.api.enums.CheckoutCandidateErrorCode.CLIENT_NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        if (errorCode instanceof com.paytech.compliance.api.enums.QualificationErrorCode qualification) {
            return switch (qualification) {
                case QUALIFICATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case QUALIFICATION_CHANGED, QUALIFICATION_NOT_ACTIVE -> HttpStatus.CONFLICT;
                case QUALIFICATION_INVALID -> HttpStatus.UNPROCESSABLE_CONTENT;
            };
        }
        if (errorCode instanceof com.paytech.client.contract.api.enums.ClientDraftErrorCode cct) {
            return switch (cct) {
                case REVIEW_CASE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CLASSIFICATION_INVALID -> HttpStatus.UNPROCESSABLE_CONTENT;
                case CLASSIFICATION_CONFIRMED, APPLICATION_NOT_EDITABLE -> HttpStatus.CONFLICT;
                default -> defaultStatus;
            };
        }
        return defaultStatus;
    }
}
