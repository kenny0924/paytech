package com.paytech.commercial.test.base.write;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.checker.write.BusinessChangeChecker;
import com.paytech.commercial.test.checker.write.CommercialWriteResponseChecker;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;
import com.paytech.commercial.test.mocker.local.agreement.PricingProfileWriteMocker;
import com.paytech.commercial.test.mocker.local.catalog.ProductCatalogMocker;
import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.json.JsonMapper;

/**
 * 合同与画像运营写入接口的公共入口：真实 HTTP 调用、操作者请求头与共用 Mocker / Checker；不含 @Test
 * <li>两级初始化：CommercialMockTestBase 全局检查空基线并绑定事件观察队列，具体类的 setUpClassMocks 再准备本类公共数据。</li>
 */
public abstract class AbstractCommercialWriteApiTest extends CommercialMockTestBase {
    /** 本类请求 ID。 */
    protected static final String REQUEST_ID = "Commercial.Write_01";

    /** 操作者 Account ID。 */
    protected static final long ACTOR_ID = AgreementBuilder.ACTOR_ID;

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected CommercialTestMapper testMapper;

    @Autowired
    protected BusinessChangeChecker history;

    @Autowired
    protected ProductCatalogMocker catalog;

    @Autowired
    protected AgreementWriteMocker agreements;

    @Autowired
    protected PricingProfileWriteMocker profiles;

    /** 写入合同（AgreementBuilder 默认 Client 与 OE）资格合格：新增或恢复 CLIENT 合同开通项提交后门禁会查询资格。 */
    protected void mockAgreementQualified() {
        compliance.mockStatus(QualificationRoleType.CLIENT, Long.toString(AgreementBuilder.CLIENT_ID),
            QualificationEvaluationStatus.ELIGIBLE);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, AgreementBuilder.ENTITY_NO,
            QualificationEvaluationStatus.ELIGIBLE);
    }

    /** 带操作者的 POST。 */
    protected CommercialWriteResponseChecker post(String path, Object body) {
        return checker(http.post(path, headers(), body));
    }

    /** 带操作者的 PUT。 */
    protected CommercialWriteResponseChecker put(String path, Object body) {
        return checker(http.exchange(HttpMethod.PUT, path, headers(), body));
    }

    /** 不带操作者的 GET。 */
    protected CommercialWriteResponseChecker get(String path) {
        return checker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)));
    }

    /** 自定义请求头的调用。 */
    protected CommercialWriteResponseChecker exchange(HttpMethod method, String path, HttpHeaders headers, Object body) {
        return checker(http.exchange(method, path, headers, body));
    }

    /** 私网调用头与操作者。 */
    protected static HttpHeaders headers() {
        HttpHeaders headers = TestRequestHeaders.internal(REQUEST_ID);
        headers.set(AgreementApi.ACTOR_HEADER, Long.toString(ACTOR_ID));
        return headers;
    }

    /** 包装响应检查器。 */
    protected CommercialWriteResponseChecker checker(EntityExchangeResult<byte[]> result) {
        return new CommercialWriteResponseChecker(result, json, REQUEST_ID);
    }
}
