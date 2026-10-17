package com.paytech.platform.support.test.base;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ValidationIssueDetail;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ExternalApiMocker;

/**
 * 外部 API 管理接口的公共调用：路径前缀、带请求体的删除、写结果与校验结果的读取。
 *
 * <p>本类不放 {@code @Test}；各测试类在自己的 BeforeEach 中准备操作员与配置数据。
 */
public abstract class ExternalApiTestBase extends OperationsApiTestBase {

    /** 管理接口路径前缀。 */
    protected static final String BASE = "/external-api-admin/v1";

    @Autowired
    protected ExternalApiMocker externalApi;

    /** 平台管理员：持有包含外部 API 查看与管理权限的内置角色。 */
    protected Operator administrator() {
        return operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 只持有外部 API 查看权限的人员。 */
    protected Operator reader() {
        long role = operationsMocker.customRole("EXTERNAL_API_READER", ExternalApiPermissions.READ);
        return operationsMocker.operator(OperatorBuilder.administrator().roles(role));
    }

    /** 以人员身份发送带请求体的 DELETE。 */
    protected PlatformResponseChecker delete(String path, Operator operator, Object body) {
        return call(HttpMethod.DELETE, path, headers(operator), body);
    }

    /** 写成功并返回写结果，检查对象类型、编码与新版本。 */
    protected static ExternalApiMutationResponseBody assertMutation(PlatformResponseChecker checker, String objectType,
        String objectCode, int version) {
        var mutation = checker.assertOk().as(ExternalApiMutationResponseBody.class);
        assertAll(() -> assertEquals(objectType, mutation.getObjectType()),
            () -> assertEquals(objectCode, mutation.getObjectCode()),
            () -> assertEquals(version, mutation.getVersion()));
        return mutation;
    }

    /** 校验接口返回的问题编码与位置，按“编码@位置”排序比较。 */
    protected static List<String> issues(PlatformResponseChecker checker) {
        return checker.assertOk().as(ValidationResponseBody.class).getIssues().stream()
            .map(ExternalApiTestBase::issue).sorted().toList();
    }

    /** 单个问题的“编码@位置”。 */
    protected static String issue(ValidationIssueDetail issue) {
        return issue.getCode() + "@" + issue.getLocation();
    }
}
