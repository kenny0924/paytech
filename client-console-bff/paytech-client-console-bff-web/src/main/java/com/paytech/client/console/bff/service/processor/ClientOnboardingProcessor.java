package com.paytech.client.console.bff.service.processor;

import com.paytech.client.console.bff.service.model.ClientOnboardingModels.*;
import com.paytech.client.console.bff.ext.rest.response.FormOptionsResponseBody;
import com.paytech.client.contract.api.enums.ClientFormCatalogStatus;
import com.paytech.client.contract.api.onboarding.response.*;
import com.paytech.client.contract.api.onboarding.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.foundation.common.error.*;
import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.FileResponseBody;
import com.paytech.risk.api.compliance.MaterialRequirementsApi;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementBody;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 组合已授权 Owner 查询；没有业务写入、持久化缓存或跨库事务。 */
@Component
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingProcessor {

    private final ClientDraftApi drafts;
    private final OnboardingFileApi files;
    private final ClientFormOptionsApi catalogs;
    private final ClientSessionProcessor sessions;
    private final MaterialRequirementsApi risk;
    private final ClientOnboardingStartApi applications;

    /** GET 只导航既有申请，不以读取页面触发创建。 */
    public StartResponseBody start(HttpServletRequest request) {
        var identity = sessions.identity(request);
        sessions.context(request);
        var result = applications.start("client-console-bff", identity[0], identity[1]);
        if (result == null || result.applications() == null || result.applications().size() > 20) {
            throw new ServiceUnavailableException();
        }
        var active = result
            .applications()
            .stream()
            .filter(a -> a.active())
            .toList();
        if (active.size() > 1) {
            throw new ServiceUnavailableException();
        }
        sessions.context(request);
        return new StartResponseBody(
            result.applications(),
            result.hasMore(),
            active.isEmpty() ? null : active.getFirst().applicationId()
        );
    }

    /** C03 并发组合非个人目录；一个 Owner 不可用时保留另一方事实。 */
    public FormOptionsResponseBody options(HttpServletRequest request, String type, String country) {
        var identity=sessions.identity(request);
        sessions.context(request);
        if (!Set.of("INDIVIDUAL", "ORGANIZATION").contains(type) || (country != null && !country.matches("[A-Z]{2}"))) {
            throw new InvalidParameterException("客户类型或国家格式无效");
        }
        var executor=Executors.newFixedThreadPool(2,Thread.ofVirtual().factory());
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        try {
            var catalogFuture=executor.submit(context(() -> catalog(identity,type,country)));
            var requirementsFuture=country==null ? null : executor.submit(context(() -> requirements(
                type.equals("ORGANIZATION") ? "ORGANIZATION_KYB" : "INDIVIDUAL_KYC",country)));
            var catalog=result(catalogFuture,deadline);
            var required=result(requirementsFuture,deadline);
            sessions.context(request);
            return new FormOptionsResponseBody(type,country,catalog==null ? "UNAVAILABLE" : catalog.getCatalogStatus(),
                catalog==null ? List.of() : catalog.getOrganizationTypes(),
                catalog==null ? List.of() : catalog.getBusinessModels(),
                catalog==null ? List.of() : catalog.getIdentifierTypes(),
                country==null ? "COUNTRY_REQUIRED" : required==null ? "UNAVAILABLE" : "AVAILABLE",
                required==null ? List.of() : required);
        } finally { executor.shutdownNow(); }
    }

    /** CCT 目录必须属于准确请求维度；未知状态与缺失列表显式不可用。 */
    ClientFormOptionsResponseBody catalog(String[] identity,String type,String country) {
        var value=catalogs.options("client-console-bff",identity[0],identity[1],type,country);
        if(value==null || !type.equals(value.getClientType()) || !Objects.equals(country,value.getCountryCode())
            || value.getOrganizationTypes()==null || value.getBusinessModels()==null || value.getIdentifierTypes()==null
            || value.getOrganizationTypes().size()>200 || value.getBusinessModels().size()>200 || value.getIdentifierTypes().size()>200) {
            throw new ServiceUnavailableException();
        }
        try { ClientFormCatalogStatus.valueOf(value.getCatalogStatus()); }
        catch(IllegalArgumentException | NullPointerException invalid) { throw new ServiceUnavailableException(); }
        if("AVAILABLE".equals(value.getCatalogStatus()) && (value.getBusinessModels().isEmpty() || value.getIdentifierTypes().isEmpty()
            || ("ORGANIZATION".equals(type) && value.getOrganizationTypes().isEmpty()))) { throw new ServiceUnavailableException(); }
        if(value.getIdentifierTypes().stream().anyMatch(i -> i==null || i.getIssuers()==null || i.getIssuers().isEmpty() || i.getIssuers().size()>50)) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 文件和要求并发只读聚合；部分依赖失败显式 unavailable，不隐藏 CCT 引用。 */
    public ApplicationResponseBody read(HttpServletRequest request, String applicationId) {
        var identity = sessions.identity(request);
        sessions.context(request);
        var draft = drafts.read("client-console-bff", identity[0], identity[1], applicationId);
        validate(draft, applicationId);
        var executor = Executors.newFixedThreadPool(8, Thread.ofVirtual().factory());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        try {
            var subjects = subjects(draft);
            var required = new LinkedHashMap<String, Future<List<RequirementBody>>>();
            for (var subject : subjects) {
                if (subject.countryCode() != null) {
                    required.put(
                        subject.subjectPartyId(),
                        executor.submit(context(() -> requirements(subject.qualificationType(), subject.countryCode())))
                    );
                }
            }
            var metadata = new LinkedHashMap<String, Future<FileResponseBody>>();
            for (var doc : draft.documents()) {
                metadata.computeIfAbsent(doc.fileId(), id -> executor.submit(context(() -> file(identity, id))));
            }
            var material = subjects
                .stream()
                .map(subject -> {
                    var list = result(required.get(subject.subjectPartyId()), deadline);
                    String status = subject.countryCode() == null
                        ? "COUNTRY_REQUIRED"
                        : list == null
                            ? "UNAVAILABLE"
                            : "AVAILABLE";
                    return new MaterialSubject(
                        subject.subjectPartyId(),
                        subject.qualificationType(),
                        subject.countryCode(),
                        status,
                        list == null ? List.of() : list
                    );
                })
                .toList();
            var documents = draft
                .documents()
                .stream()
                .map(doc -> {
                    var file = result(metadata.get(doc.fileId()), deadline);
                    return new Document(
                        doc.documentId(),
                        doc.subjectPartyId(),
                        doc.requirementCode(),
                        doc.documentType(),
                        doc.fileId(),
                        doc.subjectInScope(),
                        file == null ? "UNAVAILABLE" : "AVAILABLE",
                        file
                    );
                })
                .toList();
            sessions.context(request);
            return new ApplicationResponseBody(
                draft.applicationId(),
                draft.applicationNo(),
                draft.clientId(),
                draft.clientType(),
                draft.subjectPartyId(),
                draft.status(),
                draft.savedAt(),
                draft.editable(),
                draft.values(),
                draft.people(),
                draft.relations(),
                documents,
                material
            );
        } finally {
            executor.shutdownNow();
        }
    }

    /** 已发布代码必须识别，未知状态不默认开放下载。 */
    FileResponseBody file(String[] identity, String id) {
        var value = files.read(identity[0], identity[1], id);
        if (value == null || !id.equals(value.fileId()) || value.allowedActions() == null) {
            throw new ServiceUnavailableException();
        }
        FileScanStatus.fromCode(value.scanStatus());
        FileObjectStatus.fromCode(value.objectStatus());
        if (
            value
                .allowedActions()
                .stream()
                .anyMatch(a -> !"DOWNLOAD".equals(a))
        ) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 规则目录只允许真实、唯一且有界的条目；Program 身份不投影给浏览器。 */
    List<RequirementBody> requirements(String type, String country) {
        var value = risk.read("client-console-bff", type, country);
        if (value == null || value.items() == null || value.items().isEmpty() || value.items().size() > 1024) {
            throw new ServiceUnavailableException();
        }
        var codes = new HashSet<String>();
        for (var item : value.items()) {
            if (
                item == null ||
                item.requirementCode() == null ||
                !item.requirementCode().matches("[A-Z][A-Z0-9_]{0,63}") ||
                item.documentType() == null ||
                !item.documentType().matches("[A-Z][A-Z0-9_]{0,63}") ||
                !codes.add(item.requirementCode())
            ) {
                throw new ServiceUnavailableException();
            }
        }
        return value.items();
    }

    /** 主主体坐标来自 CCT；只为仍有关联的人员查询要求。 */
    List<MaterialSubject> subjects(ClientDraftResponseBody draft) {
        var rows = new ArrayList<MaterialSubject>();
        boolean organization = draft.clientType().equals("ORGANIZATION");
        rows.add(
            subject(
                draft.subjectPartyId(),
                organization ? "ORGANIZATION_KYB" : "INDIVIDUAL_KYC",
                draft.values(),
                organization
            )
        );
        for (var person : draft.people()) {
            if (
                draft
                    .relations()
                    .stream()
                    .anyMatch(
                        relation ->
                            relation.relatedPartyId().equals(person.partyId()) &&
                            Set.of("DRAFT", "ACTIVE").contains(relation.status())
                    )
            ) {
                rows.add(subject(person.partyId(), "RELATED_PERSON_KYC", person.values(), false));
            }
        }
        return rows;
    }

    /** 未填国家只提示补充，不自动替换为国籍或默认国家。 */
    MaterialSubject subject(String party, String type, Map<String, Map<String, String>> values, boolean organization) {
        String country = values
            .getOrDefault(organization ? "organization" : "individual", Map.of())
            .get(organization ? "incorporationCountry" : "residenceCountry");
        if (country != null && !country.matches("[A-Z]{2}")) {
            throw new ServiceUnavailableException();
        }
        return new MaterialSubject(party, type, country, "COUNTRY_REQUIRED", List.of());
    }

    /** 限制响应工作量，并拒绝 Owner 身份不匹配。 */
    void validate(ClientDraftResponseBody draft, String id) {
        if (
            draft == null ||
            !id.equals(draft.applicationId()) ||
            draft.subjectPartyId() == null ||
            !draft.subjectPartyId().matches("[1-9][0-9]{0,18}") ||
            !Set.of("INDIVIDUAL", "ORGANIZATION").contains(draft.clientType()) ||
            draft.people() == null ||
            draft.relations() == null ||
            draft.documents() == null ||
            draft.values() == null ||
            draft.people().size() > 100 ||
            draft.documents().size() > 500
        ) {
            throw new ServiceUnavailableException();
        }
    }

    /** 并发只读任务继承当前请求日志坐标，结束后清除虚拟线程上下文。 */
    <T> Callable<T> context(Supplier<T> action) {
        var values = org.slf4j.MDC.getCopyOfContextMap();
        return () -> {
            if (values != null) {
                org.slf4j.MDC.setContextMap(values);
            }
            try {
                return action.get();
            } finally {
                org.slf4j.MDC.clear();
            }
        };
    }

    /** 有界等待只影响展示；认证失败仍直接终止整页。 */
    <T> T result(Future<T> future, long deadline) {
        if (future == null) {
            return null;
        }
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException();
        } catch (ExecutionException failure) {
            if (
                failure.getCause() instanceof FailException rejected &&
                "CLIENT_SESSION_INVALID".equals(rejected.getCode())
            ) {
                throw rejected;
            }
            return null;
        } catch (TimeoutException unavailable) {
            future.cancel(true);
            return null;
        }
    }
}
