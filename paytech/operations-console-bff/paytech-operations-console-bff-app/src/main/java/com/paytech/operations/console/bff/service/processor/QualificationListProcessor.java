package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.result;
import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.integration.QualificationAdminClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.qualificationadmin.response.QualificationListItemResponseBody;
import com.paytech.operations.console.bff.service.model.qualificationadmin.response.QualificationSubjectResponseBody;
import com.paytech.operations.console.bff.service.processor.request.QualificationListRequest;

import lombok.AllArgsConstructor;

/**
 * B8 资格列表：compliance 分页为主体，再为当前页的主体补 client-master 名称
 * <li>client-master 没有按 ID 批量读取的接口：页内主体去重后并发逐个读取（同时最多 8 个），共享 3 秒总等待上限。</li>
 * <li>名称读取失败、超时或主体不存在只让该行 subjectName 为 null，不影响列表；compliance 失败整页失败。</li>
 * <li>未知角色类型的行不读取名称；子线程沿用当前请求 ID；操作者会话或权限失效属于整页失败。</li>
 */
@Component
@AllArgsConstructor
public class QualificationListProcessor
    extends AbstractProcessor<QualificationListRequest, PagingResponseBody<QualificationListItemResponseBody>> {
    /** 名称读取的总等待上限。 */
    static final long DEADLINE_SECONDS = 3;

    /** 同时读取 client-master 的上限，避免大页对 Owner 形成突发并发。 */
    static final int CONCURRENT_LOOKUPS = 8;

    private final EntityAdminClient entityAdmin;
    private final QualificationAdminClient qualifications;

    @Override
    protected PagingResponseBody<QualificationListItemResponseBody> processRequest(QualificationListRequest request) {
        var page = qualifications.list(request.getOperator(), request.getRoleType(), request.getRoleId(),
            request.getQualificationType(), request.getStatus(), request.getPage(), request.getSize());
        Map<String, String> names = subjectNames(request.getOperator(), page.items());
        return page.map(item -> new QualificationListItemResponseBody(item, names.get(subjectKey(item))));
    }

    /**
     * 并发读取页内主体名称
     *
     * @param operator 当前操作人
     * @param items 当前页资格
     * @return 主体键到展示名称；读取失败的主体不在结果中
     */
    Map<String, String> subjectNames(OperatorContext operator, List<QualificationResponseBody> items) {
        Map<String, QualificationResponseBody> subjects = new LinkedHashMap<>();
        for (var item : items) {
            if (roleTypeOf(item.getRoleType()) != null && item.getRoleId() != null) {
                subjects.putIfAbsent(subjectKey(item), item);
            }
        }
        Map<String, String> names = new HashMap<>();
        if (subjects.isEmpty()) {
            return names;
        }
        var permits = new Semaphore(CONCURRENT_LOOKUPS);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
            Map<String, Future<QualificationSubjectResponseBody>> lookups = new LinkedHashMap<>();
            subjects.forEach((key, item) -> lookups.put(key, executor.submit(withRequestContext(
                () -> lookup(permits, operator, roleTypeOf(item.getRoleType()), item.getRoleId())))));
            lookups.forEach((key, future) -> {
                var subject = result(future, deadline);
                if (subject != null) {
                    names.put(key, subject.getDisplayName());
                }
            });
        }
        return names;
    }

    /** 在并发上限内读取一个主体；等待许可时被取消直接结束。 */
    QualificationSubjectResponseBody lookup(Semaphore permits, OperatorContext operator, QualificationRoleType roleType,
        String roleId) {
        try {
            permits.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException();
        }
        try {
            return entityAdmin.subject(operator, roleType, roleId);
        } finally {
            permits.release();
        }
    }

    /** 主体去重键。 */
    static String subjectKey(QualificationResponseBody item) {
        return item.getRoleType() + "/" + item.getRoleId();
    }

    /** 按 Code 解析资格角色类型；未知 Code 返回 null。 */
    static QualificationRoleType roleTypeOf(String code) {
        return Arrays.stream(QualificationRoleType.values()).filter(type -> type.getCode().equals(code)).findFirst().orElse(null);
    }
}
