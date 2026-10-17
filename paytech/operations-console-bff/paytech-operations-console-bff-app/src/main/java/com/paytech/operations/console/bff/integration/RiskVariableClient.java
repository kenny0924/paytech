package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.RiskVariableModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/** 已授权请求的变量 Owner 边界；不编排跨系统事务。 */
public interface RiskVariableClient {
    /** 转发 getAuthoringContext 对应的准确 Owner 操作。 */
    VariableManagementContextResponseBody getAuthoringContext(OperatorContext context, String pointCode);
    /** 转发 listVariables 对应的准确 Owner 操作。 */
    VariableListResponseBody listVariables(OperatorContext context, String keyword, String sourceType, String valueType, String status, String pointCode, String usage, String nodeType, String codes, Integer page, Integer size);
    /** 转发 getVariable 对应的准确 Owner 操作。 */
    VariableDetailResponseBody getVariable(OperatorContext context, String variableCode);
    /** 转发 createVariable 对应的准确 Owner 操作。 */
    VariableDetailResponseBody createVariable(OperatorContext context, CreateVariableRequestBody body);
    /** 转发 updateVariable 对应的准确 Owner 操作。 */
    VariableDetailResponseBody updateVariable(OperatorContext context, String variableCode, UpdateVariableRequestBody body);
    /** 转发 deleteVariable 对应的准确 Owner 操作。 */
    DeleteVariableResponseBody deleteVariable(OperatorContext context, String variableCode, String expectedVersion);
    /** 转发 listMembers 对应的准确 Owner 操作。 */
    VariableMembersResponseBody listMembers(OperatorContext context, String variableCode, String keyword, Integer page, Integer size, String expectedVersion);
    /** 转发 replaceMembers 对应的准确 Owner 操作。 */
    ReplaceMembersResponseBody replaceMembers(OperatorContext context, String variableCode, ReplaceMembersRequestBody body);
    /** 转发 getReferences 对应的准确 Owner 操作。 */
    VariableReferencesResponseBody getReferences(OperatorContext context, String variableCode, String scope, String cursor, Integer size);
}
