package com.paytech.operations.console.bff.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.paytech.operations.console.bff.domain.enums.TaxPageStatus;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxReportingStatus;
import com.paytech.tax.api.response.TaxCalculationResponseBody;
import com.paytech.tax.api.response.TaxComponentResponseBody;
import com.paytech.tax.api.response.TaxDocumentResponseBody;
import com.paytech.tax.api.response.TaxEvidenceResponseBody;
import com.paytech.tax.api.response.TaxOrderResponseBody;
import com.paytech.tax.api.response.TaxPolicyResponseBody;
import com.paytech.tax.api.response.TaxRegistrationResponseBody;
import com.paytech.tax.api.response.TaxTransactionResponseBody;

import lombok.Getter;

/**
 * 税务查询页一次请求的事实：tax 订单查询结果及从中选出的当前报价、首个报价、最近最终报价、正式交易与税票
 * <li>只做纯内存选择与判断，不查询、不调用外部服务；tax 已按 revision 升序返回计算，这里仍按 revision 排序以防顺序漂移。</li>
 * <li>本期一笔订单至多一条 SALE 正式交易；多条时取最早登记的一条。税票优先取 ISSUED。</li>
 */
@Getter
public class TaxOrderPageContext {
    private final TaxOrderResponseBody order;
    private final List<TaxCalculationResponseBody> calculations;
    private final TaxCalculationResponseBody current;
    private final TaxTransactionResponseBody transaction;
    private final TaxDocumentResponseBody document;

    /** 从已核对形状的 tax 响应选出各页面要素。 */
    public TaxOrderPageContext(TaxOrderResponseBody order) {
        this.order = order;
        this.calculations = order.getCalculations().stream()
            .sorted(Comparator.comparing(TaxCalculationResponseBody::getSourceRevision)).toList();
        this.current = calculations.getLast();
        this.transaction = order.getTransactions().stream().filter(Objects::nonNull)
            .min(Comparator.comparing(TaxTransactionResponseBody::getRegisteredAt,
                Comparator.nullsLast(Comparator.naturalOrder()))).orElse(null);
        this.document = transaction == null || transaction.getDocuments() == null ? null
            : transaction.getDocuments().stream().filter(Objects::nonNull)
                .min(Comparator.comparing(doc -> !TaxDocumentIssueStatus.ISSUED.getCode().equals(doc.getIssueStatus())))
                .orElse(null);
    }

    /** 首个报价（建单）。 */
    public TaxCalculationResponseBody first() {
        return calculations.getFirst();
    }

    /** 最近一次最终报价；没有为 null。 */
    public TaxCalculationResponseBody latestFinal() {
        return calculations.reversed().stream()
            .filter(calc -> TaxCalculationStage.FINAL.getCode().equals(calc.getStage())).findFirst().orElse(null);
    }

    /** 当前报价不由平台计税。 */
    public boolean notManaged() {
        return TaxCalculationStatus.NOT_MANAGED.getCode().equals(current.getStatus());
    }

    /** 当前报价被阻断。 */
    public boolean blocked() {
        return TaxCalculationStatus.BLOCKED.getCode().equals(current.getStatus());
    }

    /** 当前报价已算出税额。 */
    public boolean ready() {
        return TaxCalculationStatus.READY.getCode().equals(current.getStatus());
    }

    /** 已有正式交易。 */
    public boolean registered() {
        return transaction != null;
    }

    /** 税票已开具。 */
    public boolean issued() {
        return document != null && TaxDocumentIssueStatus.ISSUED.getCode().equals(document.getIssueStatus());
    }

    /** 底稿缺法定汇率等待补齐。 */
    public boolean reportingPending() {
        return transaction != null && TaxReportingStatus.PENDING.getCode().equals(transaction.getReportingStatus());
    }

    /** 标题处的页面状态。 */
    public TaxPageStatus pageStatus() {
        if (notManaged()) {
            return TaxPageStatus.NOT_MANAGED;
        }
        if (registered()) {
            return issued() ? TaxPageStatus.ISSUED : TaxPageStatus.REGISTERED;
        }
        if (blocked()) {
            return TaxPageStatus.BLOCKED;
        }
        return TaxCalculationStage.FINAL.getCode().equals(current.getStage()) ? TaxPageStatus.QUOTED
            : TaxPageStatus.ESTIMATED;
    }

    /** 正式交易依据的计算；交易引用的计算不在本订单时取当前报价。 */
    public TaxCalculationResponseBody registeredCalculation() {
        return calculations.stream().filter(calc -> calc.getCalculationId().equals(transaction.getCalculationId()))
            .findFirst().orElse(current);
    }

    /** 某计算全部行内组件。 */
    public static List<TaxComponentResponseBody> components(TaxCalculationResponseBody calc) {
        if (calc.getLines() == null) {
            return List.of();
        }
        return calc.getLines().stream().filter(line -> line.getComponents() != null)
            .flatMap(line -> line.getComponents().stream()).toList();
    }

    /**
     * 某计算作为供应地依据的证据
     * <li>优先 placeOfSupplyBasis 为 true 的一条；没有时取本阶段对应的证据类型（FINAL 账单国家、PROVISIONAL 声明国家）；都没有为 null。</li>
     */
    public static TaxEvidenceResponseBody placeOfSupply(TaxCalculationResponseBody calc) {
        if (calc.getEvidence() == null) {
            return null;
        }
        var basis = calc.getEvidence().stream().filter(item -> Boolean.TRUE.equals(item.getPlaceOfSupplyBasis()))
            .findFirst();
        if (basis.isPresent()) {
            return basis.get();
        }
        String stageType = TaxCalculationStage.FINAL.getCode().equals(calc.getStage())
            ? TaxEvidenceType.BILLING_COUNTRY.getCode() : TaxEvidenceType.DECLARED_COUNTRY.getCode();
        return calc.getEvidence().stream().filter(item -> stageType.equals(item.getType())).findFirst().orElse(null);
    }

    /** 按 ID 查找本订单引用的税务注册；没有为 null。 */
    public TaxRegistrationResponseBody registration(String registrationId) {
        return registrationId == null ? null : order.getRegistrations().stream()
            .filter(item -> registrationId.equals(item.getRegistrationId())).findFirst().orElse(null);
    }

    /** 按 ID 查找本订单引用的地区包；没有为 null。 */
    public TaxPolicyResponseBody policy(String policyId) {
        return policyId == null ? null : order.getPolicies().stream()
            .filter(item -> policyId.equals(item.getPolicyId())).findFirst().orElse(null);
    }

    /** 某计算第一个带注册的组件对应的税务注册；没有为 null。 */
    public TaxRegistrationResponseBody registrationOf(TaxCalculationResponseBody calc) {
        return components(calc).stream().map(TaxComponentResponseBody::getRegistrationId).filter(Objects::nonNull)
            .map(this::registration).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
