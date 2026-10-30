package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.result;
import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.CommercialAgreementClient;
import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.integration.QualificationAdminClient;
import com.paytech.operations.console.bff.integration.RiskUnderwritingClient;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementCapabilityDetail;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementDetail;
import com.paytech.operations.console.bff.service.model.CommercialListModels.AgreementListItem;
import com.paytech.operations.console.bff.service.model.CommercialListModels.AgreementListRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * 上线复核清单系统项的读取（B5、Q-12）：并发读取有效承保（risk-decision）、Client 资格（compliance）与 CLIENT 合同及开通项（commercial）
 * <li>每个来源失败或超过调用方给出的截止时间只标为不可用，不伪造"未满足"；操作者会话或权限失效直接抛出。</li>
 * <li>合同取 ACTIVE 的 CLIENT 合同，没有时取最近创建的一份；开通项只看合同成员资格 ACTIVE 的项。</li>
 */
@Component
@AllArgsConstructor
public class LimitTierChecklistAssembler {
    /** 合同列表读取条数：一个 Client 的 CLIENT 合同远少于此数。 */
    static final int AGREEMENT_PAGE_SIZE = 20;

    /** commercial 合同类型 CLIENT。 */
    static final String CLIENT_AGREEMENT = "CLIENT";

    /** commercial 合同状态与开通项成员资格 ACTIVE。 */
    static final String ACTIVE = "ACTIVE";

    private final QualificationAdminClient qualifications;
    private final CommercialAgreementClient agreements;
    private final RiskUnderwritingClient underwritings;
    private final CommercialConsoleQueryClient commercialQueries;

    /**
     * 在调用方的执行器上并发读取三个来源并等待到截止时间
     *
     * @param executor 调用方的虚拟线程执行器
     * @param deadline System.nanoTime 截止时间
     * @param operator 当前操作人
     * @param clientId Client ID
     * @return 读取结果；不可用的来源为 null
     */
    public LimitTierChecklistReading read(ExecutorService executor, long deadline, OperatorContext operator, String clientId) {
        var current = executor.submit(withRequestContext(() -> underwritings.current(operator, clientId)));
        var qualification = executor.submit(withRequestContext(
            () -> qualifications.subjectQualifications(operator, QualificationRoleType.CLIENT, clientId)));
        var agreement = executor.submit(withRequestContext(() -> agreement(clientId)));
        return new LimitTierChecklistReading(result(current, deadline), result(qualification, deadline),
            result(agreement, deadline));
    }

    /**
     * 读取 Risk 自有事实：失败原样抛出，超过截止时间按依赖超时处理
     *
     * @param future 已提交的读取
     * @param deadline System.nanoTime 截止时间
     * @param <T> 结果类型
     * @return 读取结果
     */
    public static <T> T required(Future<T> future, long deadline) {
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException();
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, timeout);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new ServiceUnavailableException(failure.getCause());
        }
    }

    /** CLIENT 合同及其开通项；没有合同时为 NONE。 */
    LimitTierChecklistReading.AgreementReading agreement(String clientId) {
        var page = commercialQueries.listAgreements(new AgreementListRequestBody(clientId, null, CLIENT_AGREEMENT, List.of(),
            null, null, 1, AGREEMENT_PAGE_SIZE));
        AgreementListItem chosen = page.items().stream().filter(item -> ACTIVE.equals(item.getStatus())).findFirst()
            .orElse(page.items().isEmpty() ? null : page.items().getFirst());
        if (chosen == null) {
            return LimitTierChecklistReading.AgreementReading.NONE;
        }
        AgreementDetail detail = agreements.detail(chosen.getAgreementId());
        List<AgreementCapabilityDetail> capabilities = detail.getCapabilities() == null ? List.of()
            : detail.getCapabilities().stream().filter(capability -> ACTIVE.equals(capability.getStatus())).toList();
        return new LimitTierChecklistReading.AgreementReading(detail.getAgreementId(), detail.getStatus(),
            codes(capabilities, true), codes(capabilities, false));
    }

    /** ENABLED 或非 ENABLED 的开通项代码，去重排序。 */
    static List<String> codes(List<AgreementCapabilityDetail> capabilities, boolean enabled) {
        return capabilities.stream()
            .filter(capability -> LimitTierChecklistReading.ENABLED.equals(capability.getExecutionStatus()) == enabled)
            .map(AgreementCapabilityDetail::getCapabilityCode).distinct().sorted(Comparator.naturalOrder()).toList();
    }

    /** 当前有效（实时判定 ELIGIBLE）的资格；没有为 null。 */
    static QualificationResponseBody eligible(List<QualificationResponseBody> items) {
        return items.stream().filter(item -> QualificationEvaluationStatus.ELIGIBLE.getCode().equals(item.getEffectiveStatus()))
            .findFirst().orElse(null);
    }
}
