package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.LocalizationPaths.APPLICATION;
import static com.paytech.operations.console.bff.ext.rest.LocalizationPaths.LOCALE;
import static com.paytech.operations.console.bff.ext.rest.LocalizationPaths.NAMESPACE;
import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.PlatformLocalizationClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;

/**
 * 多语言「语言与应用」页的浏览器入口（语言 3、应用 4、命名空间 4），全部简单转发 Platform 同路径
 * <li>Gateway 绑定：查询 I18N_VIEW，写操作 I18N_EDIT，并经 PLT-I02 实时授权；Platform 再次校验。</li>
 * <li>未知或重复 Query、查询带 Body、请求体未知字段在访问 Platform 前拒绝。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping(LocalizationPaths.ADMIN)
public class LocalizationCatalogController {
    private final PlatformLocalizationClient client;

    /** 全部语言，含 DISABLED。 */
    @GetMapping("/locales")
    public LocaleListResponseBody listLocales(HttpServletRequest servletRequest, OperatorContext context) {
        requireQueryKeys(servletRequest, Set.of());
        return client.listLocales(context);
    }

    /** 新增语言。 */
    @PostMapping("/locales")
    public LocaleResponseBody createLocale(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid CreateLocaleRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createLocale(context, body);
    }

    /** 修改语言名称、方向、状态与排序。 */
    @PutMapping("/locales/{localeCode}")
    public LocaleResponseBody updateLocale(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("localeCode") @Pattern(regexp = LOCALE) String localeCode,
        @RequestBody @NotNull @Valid UpdateLocaleRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.updateLocale(context, localeCode, body);
    }

    /** 全部应用。 */
    @GetMapping("/applications")
    public ApplicationListResponseBody listApplications(HttpServletRequest servletRequest, OperatorContext context) {
        requireQueryKeys(servletRequest, Set.of());
        return client.listApplications(context);
    }

    /** 应用详情：设置、当前发布与覆盖率摘要。 */
    @GetMapping("/applications/{applicationCode}")
    public ApplicationDetailResponseBody getApplication(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getApplication(context, applicationCode);
    }

    /** 新增应用。 */
    @PostMapping("/applications")
    public ApplicationResponseBody createApplication(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid CreateApplicationRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createApplication(context, body);
    }

    /** 修改应用名称、默认语言、必须覆盖语言与阈值。 */
    @PutMapping("/applications/{applicationCode}")
    public ApplicationResponseBody updateApplication(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestBody @NotNull @Valid UpdateApplicationRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.updateApplication(context, applicationCode, body);
    }

    /** 应用的全部命名空间，含 key 数。 */
    @GetMapping("/applications/{applicationCode}/namespaces")
    public NamespaceListResponseBody listNamespaces(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode) {
        requireQueryKeys(servletRequest, Set.of());
        return client.listNamespaces(context, applicationCode);
    }

    /** 新建命名空间。 */
    @PostMapping("/applications/{applicationCode}/namespaces")
    public NamespaceResponseBody createNamespace(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestBody @NotNull @Valid CreateNamespaceRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createNamespace(context, applicationCode, body);
    }

    /** 修改命名空间名称、说明与排序。 */
    @PutMapping("/applications/{applicationCode}/namespaces/{namespaceCode}")
    public NamespaceResponseBody updateNamespace(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @PathVariable("namespaceCode") @Pattern(regexp = NAMESPACE) String namespaceCode,
        @RequestBody @NotNull @Valid UpdateNamespaceRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.updateNamespace(context, applicationCode, namespaceCode, body);
    }

    /** 删除其下没有任何 key 的命名空间；成功 200 无正文。 */
    @DeleteMapping("/applications/{applicationCode}/namespaces/{namespaceCode}")
    public void deleteNamespace(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @PathVariable("namespaceCode") @Pattern(regexp = NAMESPACE) String namespaceCode,
        @RequestParam("expectedVersion") @Positive Integer expectedVersion) {
        requireQueryKeys(servletRequest, Set.of("expectedVersion"));
        client.deleteNamespace(context, applicationCode, namespaceCode, expectedVersion);
    }
}
