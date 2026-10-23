package com.paytech.platform.support.ext.rest.localization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.localization.LocalizationCatalogApi;
import com.paytech.platform.support.api.localization.request.CreateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.CreateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.CreateNamespaceRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateNamespaceRequestBody;
import com.paytech.platform.support.api.localization.response.ApplicationDetailResponseBody;
import com.paytech.platform.support.api.localization.response.ApplicationListResponseBody;
import com.paytech.platform.support.api.localization.response.ApplicationResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleListResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleResponseBody;
import com.paytech.platform.support.api.localization.response.NamespaceListResponseBody;
import com.paytech.platform.support.api.localization.response.NamespaceResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.localization.service.processor.CreateApplicationProcessor;
import com.paytech.platform.support.localization.service.processor.CreateLocaleProcessor;
import com.paytech.platform.support.localization.service.processor.CreateNamespaceProcessor;
import com.paytech.platform.support.localization.service.processor.DeleteNamespaceProcessor;
import com.paytech.platform.support.localization.service.processor.GetApplicationProcessor;
import com.paytech.platform.support.localization.service.processor.ListApplicationsProcessor;
import com.paytech.platform.support.localization.service.processor.ListLocalesProcessor;
import com.paytech.platform.support.localization.service.processor.ListNamespacesProcessor;
import com.paytech.platform.support.localization.service.processor.UpdateApplicationProcessor;
import com.paytech.platform.support.localization.service.processor.UpdateLocaleProcessor;
import com.paytech.platform.support.localization.service.processor.UpdateNamespaceProcessor;
import com.paytech.platform.support.localization.service.processor.request.ApplicationQuery;
import com.paytech.platform.support.localization.service.processor.request.OperatorQuery;

import lombok.AllArgsConstructor;

/** 语言、应用与命名空间维护的协议适配；权限、锁与事务委托 localization 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class LocalizationCatalogController implements LocalizationCatalogApi {

    private final LocalizationConverter converter;
    private final ListLocalesProcessor listLocalesProcessor;
    private final GetApplicationProcessor getApplicationProcessor;
    private final ListNamespacesProcessor listNamespacesProcessor;
    private final ListApplicationsProcessor listApplicationsProcessor;
    private final CreateLocaleProcessor createLocaleProcessor;
    private final UpdateLocaleProcessor updateLocaleProcessor;
    private final CreateNamespaceProcessor createNamespaceProcessor;
    private final DeleteNamespaceProcessor deleteNamespaceProcessor;
    private final UpdateNamespaceProcessor updateNamespaceProcessor;
    private final CreateApplicationProcessor createApplicationProcessor;
    private final UpdateApplicationProcessor updateApplicationProcessor;

    /** 全部语言。 */
    @Override
    public LocaleListResponseBody listLocales(String actor, String session) {
        return listLocalesProcessor.process(new OperatorQuery(actor, session));
    }

    /** 新增语言。 */
    @Audit(action = "I18N_LOCALE_CREATE", resourceType = "I18N_LOCALE", fallbackResourceId = "I18N_LOCALE_CATALOG")
    @Override
    public LocaleResponseBody createLocale(String actor, String session, CreateLocaleRequestBody request) {
        return createLocaleProcessor.process(converter.createLocale(actor, session, request));
    }

    /** 修改语言。 */
    @Audit(action = "I18N_LOCALE_UPDATE", resourceType = "I18N_LOCALE", fallbackResourceId = "I18N_LOCALE_CATALOG")
    @Override
    public LocaleResponseBody updateLocale(String actor, String session, String localeCode,
        UpdateLocaleRequestBody request) {
        return updateLocaleProcessor.process(converter.updateLocale(actor, session, localeCode, request));
    }

    /** 全部应用。 */
    @Override
    public ApplicationListResponseBody listApplications(String actor, String session) {
        return listApplicationsProcessor.process(new OperatorQuery(actor, session));
    }

    /** 应用详情。 */
    @Override
    public ApplicationDetailResponseBody getApplication(String actor, String session, String applicationCode) {
        return getApplicationProcessor.process(new ApplicationQuery(actor, session, applicationCode));
    }

    /** 新增应用。 */
    @Audit(action = "I18N_APPLICATION_CREATE", resourceType = "I18N_APPLICATION", fallbackResourceId = "I18N_APPLICATION_CATALOG")
    @Override
    public ApplicationResponseBody createApplication(String actor, String session,
        CreateApplicationRequestBody request) {
        return createApplicationProcessor.process(converter.createApplication(actor, session, request));
    }

    /** 修改应用。 */
    @Audit(action = "I18N_APPLICATION_UPDATE", resourceType = "I18N_APPLICATION", fallbackResourceId = "I18N_APPLICATION_CATALOG")
    @Override
    public ApplicationResponseBody updateApplication(String actor, String session, String applicationCode,
        UpdateApplicationRequestBody request) {
        return updateApplicationProcessor.process(converter.updateApplication(actor, session, applicationCode,
            request));
    }

    /** 命名空间列表。 */
    @Override
    public NamespaceListResponseBody listNamespaces(String actor, String session, String applicationCode) {
        return listNamespacesProcessor.process(new ApplicationQuery(actor, session, applicationCode));
    }

    /** 新建命名空间。 */
    @Audit(action = "I18N_NAMESPACE_CREATE", resourceType = "I18N_NAMESPACE", fallbackResourceId = "I18N_NAMESPACE_CATALOG")
    @Override
    public NamespaceResponseBody createNamespace(String actor, String session, String applicationCode,
        CreateNamespaceRequestBody request) {
        return createNamespaceProcessor.process(converter.createNamespace(actor, session, applicationCode, request));
    }

    /** 修改命名空间。 */
    @Audit(action = "I18N_NAMESPACE_UPDATE", resourceType = "I18N_NAMESPACE", fallbackResourceId = "I18N_NAMESPACE_CATALOG")
    @Override
    public NamespaceResponseBody updateNamespace(String actor, String session, String applicationCode,
        String namespaceCode, UpdateNamespaceRequestBody request) {
        return updateNamespaceProcessor.process(converter.updateNamespace(actor, session, applicationCode,
            namespaceCode, request));
    }

    /** 删除命名空间。 */
    @Audit(action = "I18N_NAMESPACE_DELETE", resourceType = "I18N_NAMESPACE", fallbackResourceId = "I18N_NAMESPACE_CATALOG")
    @Override
    public void deleteNamespace(String actor, String session, String applicationCode, String namespaceCode,
        Integer expectedVersion) {
        deleteNamespaceProcessor.process(converter.deleteNamespace(actor, session, applicationCode, namespaceCode,
            expectedVersion));
    }
}
