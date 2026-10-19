package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.operations.console.bff.ext.rest.OperationsAuthenticationController;
import com.paytech.operations.console.bff.ext.rest.GatewayConfigurationController;
import com.paytech.operations.console.bff.ext.rest.OperationsManagementController;
import com.paytech.operations.console.bff.ext.rest.RiskDecisionConfigurationController;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels;
import com.paytech.operations.console.bff.service.model.RiskTestCandidateModels;
import com.paytech.platform.support.api.authentication.OperationsAuthenticationApi;
import com.paytech.platform.support.api.authorization.OperationsAuthorizationApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.platform.support.api.operations.OperationsManagementApi;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.service.annotation.HttpExchange;

/** 只约束实际 HTTP 方法的顶层 Body 类型，不改变列表元素或内部上下文命名。 */
class OperationsHttpBodyNamingAutoTest {
    @Test
    void test_riskBrowserModelsUseExactLocalNamesWithoutOwnerVersionSuffixes() {
        assertEquals(Set.of("RuleSetQueryRequestBody", "CreateRuleSetVersionRequestBody", "RuleRequestBody",
            "ConditionNodeRequestBody", "OperandRequestBody", "AdditionalAction", "ActionParameter", "ActionParameterDescriptor", "TargetBindingDescriptor", "TargetFieldDescriptor", "RuleActionRequestBody", "FreezeRuleSetVersionRequestBody",
            "RuleAuthoringContextResponseBody", "DecisionPointDescriptorResponseBody", "DecisionInputFieldDescriptorResponseBody",
            "DecisionActionDescriptorResponseBody", "RuleSetListItemResponseBody", "RuleSetDetailResponseBody",
            "DeleteRuleSetRequestBody", "DeleteRuleSetResponseBody", "CreateRuleSetVersionResponseBody", "FreezeRuleSetVersionResponseBody"), recordNames(RiskRuleSetModels.class));
        assertEquals(Set.of("CandidateQueryRequestBody", "CreateTestCandidateRequestBody", "ValidateTestCandidateRequestBody",
            "TestCandidateListItemResponseBody", "TestCandidateDetailResponseBody", "FrozenCompositionResponseBody",
            "ReleaseStateChangeResponseBody", "ReleaseStaticCheckResponseBody", "InitialStateChangeResponseBody",
            "ValidationCheckResponseBody", "CreateTestCandidateResponseBody", "ValidateTestCandidateResponseBody"),
            recordNames(RiskTestCandidateModels.class));
    }

    private static Set<String> recordNames(Class<?> container) {
        return Arrays.stream(container.getDeclaredClasses()).filter(Class::isRecord).map(Class::getSimpleName)
            .collect(Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(classes = {OperationsAuthenticationController.class, OperationsManagementController.class,
        GatewayConfigurationController.class, RiskDecisionConfigurationController.class,
        OperationsAuthenticationApi.class, OperationsAuthorizationApi.class,
        OperationsManagementApi.class, GatewayConfigurationManagementApi.class,
        com.paytech.operations.console.bff.ext.rest.StandardApiManagementController.class,
        com.paytech.operations.console.bff.ext.rest.IntegrationManagementController.class,
        com.paytech.operations.console.bff.ext.rest.IntegrationApiManagementController.class,
        com.paytech.operations.console.bff.ext.rest.IntegrationScriptManagementController.class,
        com.paytech.operations.console.bff.ext.rest.IntegrationImplementationManagementController.class,
        com.paytech.operations.console.bff.ext.rest.ExternalApiEditorController.class,
        com.paytech.operations.console.bff.ext.rest.NotificationManagementController.class,
        com.paytech.operations.console.bff.ext.rest.WebhookManagementController.class,
        com.paytech.operations.console.bff.ext.rest.FinanceSetupController.class,
        com.paytech.operations.console.bff.ext.rest.FinanceLedgerBookController.class,
        com.paytech.operations.console.bff.ext.rest.FinanceBalanceController.class,
        com.paytech.operations.console.bff.ext.rest.FinanceSubledgerController.class,
        com.paytech.account.api.OwnerAccountQueryApi.class, com.paytech.account.api.AccountPostingApi.class,
        com.paytech.ledger.api.LedgerSetupQueryApi.class, com.paytech.ledger.api.LedgerBookQueryApi.class,
        com.paytech.ledger.api.AccountingPeriodCommandApi.class, com.paytech.account.api.BalanceQueryApi.class,
        com.paytech.operations.console.bff.ext.rest.CommercialAgreementController.class,
        com.paytech.operations.console.bff.ext.rest.CommercialPricingController.class,
        com.paytech.commercial.api.agreement.AgreementApi.class, com.paytech.commercial.api.pricing.PricingProfileApi.class})
    void test_httpBodyTypesUseExplicitRequestAndResponseSuffixes(Class<?> contract) {
        if (contract.isInterface()) {
            assertEquals(0, contract.getDeclaredClasses().length,
                () -> contract.getSimpleName() + " 的 DTO 必须放在独立 Models 容器中");
        }
        List<Method> methods = Arrays.stream(contract.getDeclaredMethods())
            .filter(OperationsHttpBodyNamingAutoTest::isHttpMethod)
            .toList();
        assertFalse(methods.isEmpty(), () -> contract.getSimpleName() + " 必须包含实际 HTTP 方法");

        for (Method method : methods) {
            String endpoint = contract.getSimpleName() + "." + method.getName();
            for (Parameter parameter : method.getParameters()) {
                if (parameter.isAnnotationPresent(RequestBody.class)) {
                    assertTrue(parameter.getType().getSimpleName().endsWith("RequestBody"),
                        () -> endpoint + " 的请求 Body 类型必须以 RequestBody 结尾");
                }
            }
            if (method.getReturnType() != void.class) {
                // 顶层为 JSON 数组时约束数组元素类型。
                Class<?> body = method.getReturnType() == List.class
                    ? org.springframework.core.ResolvableType.forMethodReturnType(method).getGeneric(0).resolve(Object.class)
                    : method.getReturnType();
                assertTrue(body.getSimpleName().endsWith("ResponseBody"),
                    () -> endpoint + " 的顶层响应类型必须以 ResponseBody 结尾");
            }
        }
    }

    private static boolean isHttpMethod(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
            || AnnotatedElementUtils.hasAnnotation(method, HttpExchange.class);
    }
}
