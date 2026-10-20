package com.paytech.ledger.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.event.AccountPostedEntryPayload;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.api.enums.AmountSource;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.enums.RuleOwnerRole;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.domain.AccountPostingBatch;
import com.paytech.ledger.domain.AccountPostingCommand;
import com.paytech.ledger.domain.JournalPostingContext;
import com.paytech.ledger.domain.LedgerAccountNo;
import com.paytech.ledger.domain.PeriodCloseContentHash;
import com.paytech.ledger.domain.TransactionPostingContext;
import com.paytech.ledger.domain.view.PostedInstructionView;
import com.paytech.ledger.domainservice.repository.AccountNoSequenceRepository;
import com.paytech.ledger.domainservice.repository.AccountingInstructionRepository;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleRepository;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleVerRepository;
import com.paytech.ledger.domainservice.repository.AccountingRuleLineRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.FinanceEntityRepository;
import com.paytech.ledger.domainservice.repository.FiscalCalendarRepository;
import com.paytech.ledger.domainservice.repository.InstructionLineRepository;
import com.paytech.ledger.domainservice.repository.JournalEntryRepository;
import com.paytech.ledger.domainservice.repository.LedgerAccountRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.service.JournalPostingService;
import com.paytech.ledger.domainservice.service.TransactionPostingService;
import com.paytech.ledger.domainservice.validator.account.AccountIdentityRule;

import lombok.AllArgsConstructor;

/**
 * 控制科目过账引擎（技术方案 2.1、设计 5.3）
 * <li>顺序：同一批次已有指令时幂等返回 → 断言事实与批次一致 → 选定生效规则版本 → 规则行（非 Owner，仅 APPLIED 批次）→ Owner 控制科目行
 * （批次流水）→ 被替代事实规则行的反向行 → 逐指令校验平衡 → 按账簿主键升序保存并过账。</li>
 * <li>SKIPPED 批次（被替代事实后到）不对事实本身应用规则包，只记流水行（所替代事实的冲回行）与其被替代事实规则行的反向行。</li>
 * <li>每个指令类型一条指令，账簿路由不变：PLATFORM_SERVICE_CHARGE_PLATFORM 进 Finance Entity 账簿，其余进事实 OE 账簿；Owner 行进承载
 * Owner 影响的指令类型（PAYMENT_SUCCEEDED、ECONOMIC_ADJUSTMENT、CHANNEL_COST_ADJUSTMENT），其账簿必须是批次的过账流主体。</li>
 * <li>Owner 控制科目账户 = (科目, 账簿主体类型, 账簿主体, NONE)；BANK_CASH 流水记 (BANK_CASH, CASH_ACCOUNT, 流水 Owner, NONE)；账户不存在时在
 * 当前事务内开户。规则行不再含 Client / Local Partner 角色。</li>
 * <li>过账日期：事实业务时间（PAYMENT 为支付成功时间，其余为批准时间）在账簿日历时区的日期所在 OPEN 期间；该期间已 CLOSED 时顺延到其后最早的
 * 未关闭期间（须为 OPEN，过账日期取该期间开始日并记 WARN，业务日期经 economic_fact_id 追溯）；没有可用 OPEN 期间时以并发冲突等待。</li>
 * <li>过账复用记账内核，锁顺序为“期间 → 账簿序号”，不锁账户、不写余额。</li>
 */
@Service
@AllArgsConstructor
public class TransactionPostingServiceImpl implements TransactionPostingService {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionPostingServiceImpl.class);

    /** 交易自动记账使用的规则集代码。 */
    static final String STANDARD_TRANSACTION_BUNDLE = "STANDARD_TRANSACTION";

    /** 人工 / 成本调整计费事实使用的规则集代码。 */
    static final String STANDARD_ADJUSTMENT_BUNDLE = "STANDARD_ADJUSTMENT";

    /** 交易事实按规则生成的指令类型，按此顺序计划。 */
    static final List<AutoInstructionType> TRANSACTION_INSTRUCTIONS = List.of(AutoInstructionType.PAYMENT_SUCCEEDED,
        AutoInstructionType.PLATFORM_SERVICE_CHARGE_PARTNER, AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM);

    /** 事实类型 → 承载 Owner 控制科目行的指令类型；调整类事实只有这一个指令类型。 */
    static final Map<String, AutoInstructionType> OWNER_INSTRUCTIONS = Map.of(
        BillingFactType.PAYMENT.getCode(), AutoInstructionType.PAYMENT_SUCCEEDED,
        BillingFactType.MANUAL_ADJUSTMENT.getCode(), AutoInstructionType.ECONOMIC_ADJUSTMENT,
        BillingFactType.COST_ADJUSTMENT.getCode(), AutoInstructionType.CHANNEL_COST_ADJUSTMENT);

    /** 调整明细行中由 account 流水承载的角色：这一侧不按规则生成，由批次流水记到控制科目。 */
    static final Set<String> SUBLEDGER_PARTY_ROLES = Set.of(FactPartyRole.CLIENT.getCode(),
        FactPartyRole.LOCAL_PARTNER.getCode());

    /** 调整明细行付方 / 收方角色 → 规则行核算对象角色；未列出且不属于明细账的角色（PLATFORM、CONSUMER）没有记账规则。 */
    static final Map<String, RuleOwnerRole> PARTY_OWNER_ROLES = Map.of(
        FactPartyRole.CHANNEL.getCode(), RuleOwnerRole.CHANNEL_ACCOUNT,
        FactPartyRole.OPERATING_ENTITY.getCode(), RuleOwnerRole.TRANSACTION_OE,
        FactPartyRole.FINANCE_ENTITY.getCode(), RuleOwnerRole.PLATFORM_FINANCE_ENTITY,
        FactPartyRole.TAX_AUTHORITY.getCode(), RuleOwnerRole.TAX_AUTHORITY);

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBookRepository ledgerBookRepository;
    private final JournalPostingService journalPostingService;
    private final ChartAccountRepository chartAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final FinanceEntityRepository financeEntityRepository;
    private final FiscalCalendarRepository fiscalCalendarRepository;
    private final InstructionLineRepository instructionLineRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountingRuleLineRepository accountingRuleLineRepository;
    private final AccountNoSequenceRepository accountNoSequenceRepository;
    private final AccountingRuleBundleRepository accountingRuleBundleRepository;
    private final AccountingInstructionRepository accountingInstructionRepository;
    private final AccountingRuleBundleVerRepository accountingRuleBundleVerRepository;

    /** 同一批次已有指令时直接返回；否则计划全部指令并按账簿主键升序过账。 */
    @Override
    public List<PostedInstructionView> post(AccountPostingCommand command) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("控制科目过账必须在调用方事务内执行");
        }
        List<AccountingInstruction> existing = accountingInstructionRepository.findBySourceEvent(
            AccountPostingBatch.SOURCE_SYSTEM, command.getBatch().postingKey());
        if (!existing.isEmpty()) {
            return existing.stream().map(this::existingView).toList();
        }
        checkFactComplete(command);
        TransactionPostingContext context = loadContext(command);
        Map<AutoInstructionType, PlannedInstruction> plans = new EnumMap<>(AutoInstructionType.class);
        if (command.getBatch().appliesRules()) {
            planRuleLines(context, plans);
        }
        planOwnerLines(context, plans);
        planSupersededReversals(context, plans);
        List<PlannedInstruction> planned = new ArrayList<>(plans.values());
        planned.forEach(plan -> finish(context, plan));
        planned.sort(Comparator.comparing(plan -> plan.book.getId()));
        return planned.stream().map(this::savePosted).toList();
    }

    /** 已有指令及其分录的视图。 */
    PostedInstructionView existingView(AccountingInstruction instruction) {
        JournalEntry entry = journalEntryRepository.findByInstruction(instruction.getId());
        return new PostedInstructionView(instruction.getId(), instruction.getLedgerBookId(),
            instructionType(instruction.getInstructionType()), entry == null ? null : entry.getId(),
            entry == null ? null : entry.getJournalSequence());
    }

    /** 按稳定编码解析指令类型；非自动记账类型返回空。 */
    static AutoInstructionType instructionType(String code) {
        for (AutoInstructionType type : AutoInstructionType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 计费事实载荷完整性断言（DTO 完整性；业务不变量由 billing 负责）
     * <li>主键、版本、类型必须与批次一致；类型、OE、币种、总额与明细行集合必须存在，每行必须有行号、组件类型与金额。</li>
     */
    void checkFactComplete(AccountPostingCommand command) {
        BillingFactPayload fact = command.getFact();
        if (fact == null) {
            throw new BizCheckFailException("计费事实缺失: 入账批次 " + command.getBatch().postingKey());
        }
        var batch = command.getBatch().getPayload();
        if (!Objects.equals(fact.getFactId(), batch.getFactId()) || !Objects.equals(fact.getFactVersion(), batch.getFactVersion())
            || !Objects.equals(fact.getFactType(), batch.getFactType())) {
            throw new BizCheckFailException("计费事实与入账批次不一致: 批次 " + batch.getPostingId() + ", 事实 "
                + fact.getFactId() + "/" + fact.getFactVersion() + "/" + fact.getFactType());
        }
        Long factId = fact.getFactId();
        requireField(factId, fact.getEntityNo(), "entity_no");
        requireField(factId, fact.getCurrency(), "currency");
        requireField(factId, fact.getGrossAmount(), "gross_amount");
        requireField(factId, fact.getLines(), "lines");
        for (BillingFactLinePayload line : fact.getLines()) {
            requireField(factId, line, "line");
            requireField(factId, line.getLineNo(), "line_no");
            requireField(factId, line.getComponentType(), "component_type");
            requireField(factId, line.getPostedAmount(), "posted_amount");
        }
    }

    /** 载荷必填字段缺失时业务拒绝。 */
    void requireField(Long factId, Object value, String field) {
        if (value == null) {
            throw new BizCheckFailException("计费事实载荷不完整: " + factId + ", 缺少 " + field);
        }
    }

    /** 校验事实类型与业务时间，选定生效规则版本；明细行按行号升序。 */
    TransactionPostingContext loadContext(AccountPostingCommand command) {
        BillingFactPayload fact = command.getFact();
        if (!OWNER_INSTRUCTIONS.containsKey(fact.getFactType())) {
            throw new BizCheckFailException("计费事实不是 PAYMENT、MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT，没有记账规则: "
                + fact.getFactId() + ", 类型 " + fact.getFactType());
        }
        boolean payment = BillingFactType.PAYMENT.getCode().equals(fact.getFactType());
        Instant businessTime = fact.getBusinessTime();
        if (businessTime == null) {
            throw new BizCheckFailException("计费事实缺少业务时间（businessTime）: " + fact.getFactId());
        }
        List<BillingFactLinePayload> lines = fact.getLines().stream()
            .sorted(Comparator.comparing(BillingFactLinePayload::getLineNo)).toList();
        AccountingRuleBundleVer ver = effectiveBundleVer(fact,
            payment ? STANDARD_TRANSACTION_BUNDLE : STANDARD_ADJUSTMENT_BUNDLE, businessTime);
        return new TransactionPostingContext(command, fact, lines, ver,
            accountingRuleLineRepository.findByVersion(ver.getId()));
    }

    /** 指定规则集的当前版本必须 ACTIVE，且生效区间 [effective_from, effective_to) 覆盖给定时点。 */
    AccountingRuleBundleVer effectiveBundleVer(BillingFactPayload fact, String bundleCode, Instant at) {
        AccountingRuleBundle bundle = accountingRuleBundleRepository.findByCode(bundleCode);
        if (bundle == null || bundle.getStatus() != ReferenceDataStatus.ACTIVE || bundle.getCurrentVerId() == null) {
            throw new BizCheckFailException("无可用自动记账规则集: " + bundleCode + ", 计费事实 " + fact.getFactId());
        }
        AccountingRuleBundleVer ver = accountingRuleBundleVerRepository.findById(bundle.getCurrentVerId());
        boolean effective = ver != null && ver.getStatus() == RuleVersionStatus.ACTIVE
            && !ver.getEffectiveFrom().isAfter(at) && (ver.getEffectiveTo() == null || at.isBefore(ver.getEffectiveTo()));
        if (!effective) {
            throw new BizCheckFailException("业务时间无生效的自动记账规则版本: 计费事实 " + fact.getFactId()
                + ", 规则集 " + bundleCode + ", 当前版本 " + bundle.getCurrentVerId() + ", 业务时间 " + at);
        }
        return ver;
    }

    /** 事实的业务时间：取载荷 businessTime（PAYMENT 为支付成功时间，人工 / 成本调整为批准时间，由 billing 决定）。 */
    static Instant businessTime(BillingFactPayload fact) {
        return fact.getBusinessTime();
    }

    /** 非 Owner 规则行：交易事实按金额来源，调整事实按明细行付方 / 收方。 */
    void planRuleLines(TransactionPostingContext context, Map<AutoInstructionType, PlannedInstruction> plans) {
        if (BillingFactType.PAYMENT.getCode().equals(context.getFact().getFactType())) {
            for (AutoInstructionType type : TRANSACTION_INSTRUCTIONS) {
                planTransactionRules(context, plans, type);
            }
            return;
        }
        AutoInstructionType type = OWNER_INSTRUCTIONS.get(context.getFact().getFactType());
        for (BillingFactLinePayload line : context.getLines()) {
            planAdjustmentLine(context, plans, type, line);
        }
    }

    /** 一个交易指令类型的规则行：取金额来源后保留的每条规则行生成一行。 */
    void planTransactionRules(TransactionPostingContext context, Map<AutoInstructionType, PlannedInstruction> plans,
        AutoInstructionType type) {
        for (AccountingRuleLine rule : applicableRules(context, type)) {
            PlannedInstruction plan = plan(context, plans, type);
            LedgerAccount account = resolveAccount(context, type, plan.book, rule.getAccountCode(), rule.getBalanceBucket(),
                resolveOwner(context, type, plan.book, rule));
            plan.addRuleLine(newLine(rule.getId(), null, account, rule.getEntrySide(),
                context.amountOf(rule.getAmountSourceCode())));
        }
    }

    /**
     * 取金额来源后保留的规则行
     * <li>来源缺失：必需行拒绝，非必需行跳过；来源金额为零：该行跳过；负数拒绝。</li>
     */
    List<AccountingRuleLine> applicableRules(TransactionPostingContext context, AutoInstructionType type) {
        List<AccountingRuleLine> applicable = new ArrayList<>();
        for (AccountingRuleLine rule : context.rulesOf(type)) {
            BigDecimal amount = context.amountOf(rule.getAmountSourceCode());
            if (amount == null && Boolean.TRUE.equals(rule.getRequiredFlag())) {
                throw reject(context, type, "必需金额来源缺失 " + rule.getAmountSourceCode().getCode());
            }
            if (amount == null || amount.signum() == 0) {
                continue;
            }
            if (amount.signum() < 0) {
                throw reject(context, type, "金额来源为负 " + rule.getAmountSourceCode().getCode());
            }
            applicable.add(rule);
        }
        return applicable;
    }

    /**
     * 一条调整明细行：付方记借方、收方记贷方（方向为 REVERSAL 时互换）
     * <li>Client / Local Partner 一侧由批次流水承载，不按规则生成；其他角色按“费用类型 × 角色 × 方向”唯一匹配规则行。</li>
     * <li>缺少付方 / 收方、金额为负时整笔拒绝；金额为零的行跳过。</li>
     */
    void planAdjustmentLine(TransactionPostingContext context, Map<AutoInstructionType, PlannedInstruction> plans,
        AutoInstructionType type, BillingFactLinePayload line) {
        if (line.getPayerRole() == null || line.getPayeeRole() == null) {
            throw reject(context, type, "明细行缺少付方或收方: 行 " + line.getLineNo());
        }
        if (line.getPostedAmount().signum() < 0) {
            throw reject(context, type, "明细行金额为负: 行 " + line.getLineNo());
        }
        if (line.getPostedAmount().signum() == 0) {
            return;
        }
        boolean reversal = FactLineDirection.REVERSAL.getCode().equals(line.getDirection());
        planAdjustmentSide(context, plans, type, line, line.getPayerRole(), line.getPayerId(),
            reversal ? EntrySide.CREDIT : EntrySide.DEBIT);
        planAdjustmentSide(context, plans, type, line, line.getPayeeRole(), line.getPayeeId(),
            reversal ? EntrySide.DEBIT : EntrySide.CREDIT);
    }

    /** 调整明细行的一侧：明细账角色跳过；其余匹配唯一规则行、解析账户并追加规则行。 */
    void planAdjustmentSide(TransactionPostingContext context, Map<AutoInstructionType, PlannedInstruction> plans,
        AutoInstructionType type, BillingFactLinePayload line, String partyRole, String partyId, EntrySide side) {
        if (SUBLEDGER_PARTY_ROLES.contains(partyRole)) {
            return;
        }
        RuleOwnerRole ownerRole = PARTY_OWNER_ROLES.get(partyRole);
        if (ownerRole == null) {
            throw reject(context, type, "角色没有记账规则 " + partyRole + ": 行 " + line.getLineNo());
        }
        List<AccountingRuleLine> matched = context.rulesOf(type).stream()
            .filter(rule -> rule.getAmountSourceCode().getCode().equals(line.getComponentType())
                && rule.getOwnerRole() == ownerRole && rule.getEntrySide() == side)
            .toList();
        if (matched.size() != 1) {
            throw reject(context, type, "费用类型 " + line.getComponentType() + " × 角色 " + partyRole + " × "
                + side.getCode() + " 匹配 " + matched.size() + " 条规则行: 行 " + line.getLineNo());
        }
        AccountingRuleLine rule = matched.getFirst();
        PlannedInstruction plan = plan(context, plans, type);
        LedgerAccount account = resolveAccount(context, type, plan.book, rule.getAccountCode(), rule.getBalanceBucket(),
            adjustmentOwner(context, type, plan.book, ownerRole, partyId));
        plan.addRuleLine(newLine(rule.getId(), null, account, side, line.getPostedAmount()));
    }

    /**
     * Owner 控制科目行：批次每条流水一行，方向与金额照抄，account_entry_id 指回流水
     * <li>承载指令的账簿必须是批次过账流的主体与币种；冲回流水（reversesEntryId 非空）与普通流水同样处理。</li>
     */
    void planOwnerLines(TransactionPostingContext context, Map<AutoInstructionType, PlannedInstruction> plans) {
        List<AccountPostedEntryPayload> entries = context.getCommand().getBatch().entries();
        if (entries.isEmpty()) {
            return;
        }
        AutoInstructionType type = OWNER_INSTRUCTIONS.get(context.getFact().getFactType());
        PlannedInstruction plan = plan(context, plans, type);
        checkStreamBook(context, type, plan.book);
        for (AccountPostedEntryPayload entry : entries) {
            Owner owner = subledgerOwner(context, type, plan.book, entry);
            LedgerAccount account = resolveAccount(context, type, plan.book, entry.getAccountCode(), BalanceBucket.NONE,
                owner);
            plan.addOwnerLine(newLine(null, entry.getEntryId(), account, EntrySide.valueOf(entry.getEntrySide()),
                entry.getAmount()));
        }
    }

    /** 承载 Owner 行的账簿必须是批次过账流的主体与币种。 */
    void checkStreamBook(TransactionPostingContext context, AutoInstructionType type, LedgerBook book) {
        var batch = context.getCommand().getBatch().getPayload();
        boolean same = book.getEntityType().getCode().equals(batch.getEntityType())
            && book.getEntityId().equals(batch.getEntityId()) && book.getCurrency().equals(batch.getCurrency());
        if (!same) {
            throw reject(context, type, "入账批次的过账流主体与 Owner 行账簿不一致: 批次 " + batch.getEntityType() + "/"
                + batch.getEntityId() + "/" + batch.getCurrency() + ", 账簿 " + book.getId());
        }
    }

    /**
     * 流水对应的总账核算对象
     * <li>Owner 控制科目：账簿主体；BANK_CASH：流水的银行账户；其余科目不属于明细账目录，拒绝。</li>
     */
    Owner subledgerOwner(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        AccountPostedEntryPayload entry) {
        if (AccountIdentityRule.isControlAccount(entry.getAccountCode())) {
            return new Owner(AccountIdentityRule.bookOwnerType(book), book.getEntityId());
        }
        OwnerAccountType accountType = OwnerAccountType.ofCode(entry.getAccountCode());
        if (accountType != null && accountType.getOwnerType() == OwnerType.CASH_ACCOUNT) {
            AccountIdentityRule.checkOwnerId(LedgerOwnerType.CASH_ACCOUNT, entry.getOwnerId());
            return new Owner(LedgerOwnerType.CASH_ACCOUNT, entry.getOwnerId());
        }
        throw reject(context, type, "入账流水科目不在明细账目录: " + entry.getAccountCode() + ", 流水 " + entry.getEntryId());
    }

    /**
     * 版本替代：被替代事实已过账指令中由规则生成的正向行（规则行方向与行方向一致）逐行取反，追加到同类型指令
     * <li>被替代事实自身的反向行不再取反，保证多次替代后净额只剩最新事实；Owner 行的冲回由批次流水自带。</li>
     * <li>被替代事实尚未过账（替代先到）时没有可取反的行。</li>
     */
    void planSupersededReversals(TransactionPostingContext context, Map<AutoInstructionType, PlannedInstruction> plans) {
        Long supersedesFactId = context.getFact().getSupersedesFactId();
        if (supersedesFactId == null) {
            return;
        }
        for (AccountingInstruction previous : accountingInstructionRepository.findByEconomicFact(supersedesFactId)) {
            AutoInstructionType type = instructionType(previous.getInstructionType());
            if (type == null || previous.getAccountingRuleBundleVerId() == null) {
                continue;
            }
            Map<Long, AccountingRuleLine> rules = accountingRuleLineRepository
                .findByVersion(previous.getAccountingRuleBundleVerId()).stream()
                .collect(Collectors.toMap(AccountingRuleLine::getId, Function.identity()));
            for (InstructionLine line : instructionLineRepository.findByInstruction(previous.getId())) {
                AccountingRuleLine rule = line.getAccountingRuleLineId() == null ? null
                    : rules.get(line.getAccountingRuleLineId());
                if (rule != null && rule.getEntrySide() == line.getEntrySide()) {
                    addReversal(context, plans, type, previous, line);
                }
            }
        }
    }

    /** 追加一条反向规则行；被替代事实的指令账簿必须与本次同类型指令账簿一致。 */
    void addReversal(TransactionPostingContext context, Map<AutoInstructionType, PlannedInstruction> plans,
        AutoInstructionType type, AccountingInstruction previous, InstructionLine line) {
        PlannedInstruction plan = plan(context, plans, type);
        if (!plan.book.getId().equals(previous.getLedgerBookId())) {
            throw reject(context, type, "被替代事实指令的账簿与本次不一致: 指令 " + previous.getId());
        }
        InstructionLine reversal = newLine(line.getAccountingRuleLineId(), null, null,
            line.getEntrySide() == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT, line.getAmount());
        reversal.setAccountId(line.getAccountId());
        plan.addReversalLine(reversal);
    }

    /** 取或建一个指令类型的计划：解析目标账簿、日历与过账期间。 */
    PlannedInstruction plan(TransactionPostingContext context, Map<AutoInstructionType, PlannedInstruction> plans,
        AutoInstructionType type) {
        PlannedInstruction existing = plans.get(type);
        if (existing != null) {
            return existing;
        }
        LedgerBook book = findOpenBook(context, type);
        FiscalCalendar calendar = findCalendar(context, type, book);
        PlannedInstruction plan = resolvePeriod(context, type, book, calendar);
        plans.put(type, plan);
        return plan;
    }

    /** 指令类型的目标账簿必须存在且 OPEN：平台侧服务费入 Finance Entity 账簿，其余入事实 OE 账簿。 */
    LedgerBook findOpenBook(TransactionPostingContext context, AutoInstructionType type) {
        BillingFactPayload fact = context.getFact();
        boolean platform = type == AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM;
        LedgerEntityType entityType = platform ? LedgerEntityType.FINANCE_ENTITY : LedgerEntityType.OPERATING_ENTITY;
        String entityId = platform ? Objects.toString(fact.getFinanceEntityId(), null) : fact.getEntityNo();
        LedgerBook book = entityId == null ? null
            : ledgerBookRepository.findByEntity(entityType, entityId, fact.getCurrency());
        if (book == null || book.getBookStatus() != BookStatus.OPEN) {
            throw reject(context, type, "账簿不存在或未开放 " + entityType.getCode() + "/" + entityId + "/"
                + fact.getCurrency());
        }
        return book;
    }

    /** 账簿主体的财务日历，决定过账日期所用时区。 */
    FiscalCalendar findCalendar(TransactionPostingContext context, AutoInstructionType type, LedgerBook book) {
        FiscalCalendar calendar = fiscalCalendarRepository.findByEntity(book.getEntityType(), book.getEntityId());
        if (calendar == null) {
            throw reject(context, type, "账簿主体缺少财务日历 " + book.getId());
        }
        return calendar;
    }

    /**
     * 过账期间（设计 8.9）：业务日期所在期间 OPEN 时直接使用；已 CLOSED 时顺延到其后最早的未关闭期间，该期间须为 OPEN，过账日期取其开始日
     *
     * @throws ConcurrentRequestException 没有可用的 OPEN 期间（期间不存在、关账中或后续期间尚未开放），等待重投或追平
     */
    PlannedInstruction resolvePeriod(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        FiscalCalendar calendar) {
        LocalDate businessDate = LocalDate.ofInstant(businessTime(context.getFact()), ZoneId.of(calendar.getTimezone()));
        AccountingPeriod period = accountingPeriodRepository.findByCalendarAndDate(calendar.getId(), businessDate);
        if (period != null && period.getStatus() == PeriodStatus.OPEN) {
            return new PlannedInstruction(type, book, period, businessDate);
        }
        if (period != null && period.getStatus() == PeriodStatus.CLOSED) {
            AccountingPeriod next = accountingPeriodRepository.findFirstUnclosedAfter(calendar.getId(), period.getEndDate());
            if (next != null && next.getStatus() == PeriodStatus.OPEN) {
                LOG.warn("迟到事实顺延到下一开放期间: 计费事实 {}, 业务日期 {}, 原期间 {}, 目标期间 {}, 过账日期 {}",
                    context.factId(), businessDate, period.getId(), next.getId(), next.getStartDate());
                return new PlannedInstruction(type, book, next, next.getStartDate());
            }
        }
        throw new ConcurrentRequestException("没有可用的 OPEN 会计期间，等待期间开放: 计费事实 " + context.factId()
            + ", 指令类型 " + type.getCode() + ", 账簿 " + book.getId() + ", 业务日期 " + businessDate
            + (period == null ? "" : ", 所属期间 " + period.getId() + " 状态 " + period.getStatus().getCode()));
    }

    /**
     * 按科目、核算对象与余额桶解析唯一账户；不存在时在当前事务内创建 ACTIVE 账户身份（只写 ledger 本库）
     * <li>科目须在账簿科目表中且可过账，身份须符合账户身份矩阵；已有账户照常记账，不检查科目启停。</li>
     * <li>新建分支：停用的科目拒绝；在同一事务分配账户号序号，唯一键冲突时整笔回滚由消息重投重来，序号空洞允许。</li>
     */
    LedgerAccount resolveAccount(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        String accountCode, BalanceBucket bucket, Owner owner) {
        ChartAccount chart = chartAccountRepository.findByChartAndCode(book.getChartId(), accountCode);
        if (chart == null || !Boolean.TRUE.equals(chart.getPostingAllowed())) {
            throw reject(context, type, "科目不存在或不可过账 " + accountCode + ", 账簿 " + book.getId());
        }
        AccountIdentityRule.checkAccountIdentity(accountCode, owner.type, bucket);
        LedgerAccount account = ledgerAccountRepository.findByIdentity(book.getId(), chart.getId(), owner.type, owner.id,
            bucket);
        if (account != null) {
            return account;
        }
        if (chart.getAccountStatus() != ChartAccountStatus.ACTIVE) {
            throw reject(context, type, "科目已停用，不能新建账户 " + accountCode + ", 账簿 " + book.getId());
        }
        account = newAccount(book, chart, owner, bucket);
        long sequence = accountNoSequenceRepository.next(book.getId(), chart.getId());
        account.setAccountNo(LedgerAccountNo.of(book, chart, bookFinanceEntity(book)).withSequence(sequence));
        ledgerAccountRepository.insertConcurrently(account);
        return account;
    }

    /** FE 账簿的财务主体（账户号主体段）；OE 账簿返回空。 */
    FinanceEntity bookFinanceEntity(LedgerBook book) {
        if (book.getEntityType() != LedgerEntityType.FINANCE_ENTITY) {
            return null;
        }
        FinanceEntity entity = financeEntityRepository.findById(Long.parseLong(book.getEntityId()));
        if (entity == null) {
            throw new ErrorException("账簿的财务主体不存在: 账簿 " + book.getId() + ", 主体 " + book.getEntityId());
        }
        return entity;
    }

    /** 按固定解释器把交易规则行的 Owner Role 解析为账户 owner_type / owner_id。 */
    Owner resolveOwner(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        AccountingRuleLine rule) {
        BillingFactPayload fact = context.getFact();
        return switch (rule.getOwnerRole()) {
            case BOOK_ENTITY -> new Owner(AccountIdentityRule.bookOwnerType(book), book.getEntityId());
            case CHANNEL_ACCOUNT -> new Owner(LedgerOwnerType.CHANNEL_ACCOUNT, clearingAccount(context, type));
            case PLATFORM_FINANCE_ENTITY -> new Owner(LedgerOwnerType.FINANCE_ENTITY,
                requireId(context, type, fact.getFinanceEntityId(), "finance_entity_id"));
            case TRANSACTION_OE -> new Owner(LedgerOwnerType.OPERATING_ENTITY, fact.getEntityNo());
            case TAX_AUTHORITY -> new Owner(LedgerOwnerType.TAX_AUTHORITY, taxAuthority(context, type));
        };
    }

    /** 可空的 Header 数字标识必须存在，返回十进制字符串。 */
    String requireId(TransactionPostingContext context, AutoInstructionType type, Long id, String field) {
        if (id == null) {
            throw reject(context, type, "缺少 " + field);
        }
        return String.valueOf(id);
    }

    /**
     * 渠道清算账户：取外部引用 CLEARING_ACCOUNT 的 refId，必须唯一
     * <li>缺失时拒绝原因同原 channel_account_id 缺失；多个不同引用无法确定渠道账户 Owner，同样拒绝。</li>
     */
    String clearingAccount(TransactionPostingContext context, AutoInstructionType type) {
        List<BillingFactRefPayload> refs = context.getFact().getRefs();
        List<String> accounts = refs == null ? List.of() : refs.stream()
            .filter(ref -> ref != null && FactRefType.CLEARING_ACCOUNT.getCode().equals(ref.getRefType()))
            .map(BillingFactRefPayload::getRefId).filter(Objects::nonNull).distinct().toList();
        if (accounts.isEmpty()) {
            throw reject(context, type, "缺少 channel_account_id");
        }
        if (accounts.size() > 1) {
            throw reject(context, type, "CLEARING_ACCOUNT 引用不唯一 " + accounts);
        }
        return accounts.get(0);
    }

    /** SELLER_TAX 组件的收方必须指向唯一且非空的税务机关。 */
    String taxAuthority(TransactionPostingContext context, AutoInstructionType type) {
        List<String> parties = context.linesOf(AmountSource.SELLER_TAX.getCode()).stream()
            .map(BillingFactLinePayload::getPayeeId).distinct().toList();
        if (parties.size() != 1 || parties.get(0) == null) {
            throw reject(context, type, "SELLER_TAX 组件税务机关缺失或不唯一 " + parties);
        }
        return parties.get(0);
    }

    /** 调整行的核算对象：渠道取外部引用 CLEARING_ACCOUNT，交易 OE 取事实头 entityNo，其余取明细行标识。 */
    Owner adjustmentOwner(TransactionPostingContext context, AutoInstructionType type, LedgerBook book, RuleOwnerRole role,
        String partyId) {
        BillingFactPayload fact = context.getFact();
        Owner owner = switch (role) {
            case CHANNEL_ACCOUNT -> new Owner(LedgerOwnerType.CHANNEL_ACCOUNT, clearingAccount(context, type));
            case TRANSACTION_OE -> new Owner(LedgerOwnerType.OPERATING_ENTITY, fact.getEntityNo());
            case PLATFORM_FINANCE_ENTITY -> new Owner(LedgerOwnerType.FINANCE_ENTITY, partyId);
            case TAX_AUTHORITY -> new Owner(LedgerOwnerType.TAX_AUTHORITY, partyId);
            case BOOK_ENTITY -> new Owner(AccountIdentityRule.bookOwnerType(book), book.getEntityId());
        };
        if (owner.id == null) {
            throw reject(context, type, "角色 " + role.getCode() + " 缺少标识");
        }
        AccountIdentityRule.checkOwnerId(owner.type, owner.id);
        return owner;
    }

    /** ACTIVE 账户身份，正常方向从科目复制；不保存余额。 */
    LedgerAccount newAccount(LedgerBook book, ChartAccount chart, Owner owner, BalanceBucket bucket) {
        Instant now = now();
        LedgerAccount account = new LedgerAccount();
        account.setId(idGenerator.nextId());
        account.setLedgerBookId(book.getId());
        account.setChartAccountId(chart.getId());
        account.setOwnerType(owner.type);
        account.setOwnerId(owner.id);
        account.setBalanceBucket(bucket);
        account.setNormalSide(chart.getNormalSide());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return account;
    }

    /** 指令行；行号与指令主键在计划完成时回填。规则行与 Owner 行的来源标识二选一。 */
    InstructionLine newLine(Long ruleLineId, Long accountEntryId, LedgerAccount account, EntrySide side,
        BigDecimal amount) {
        InstructionLine line = new InstructionLine();
        line.setId(idGenerator.nextId());
        line.setAccountingRuleLineId(ruleLineId);
        line.setAccountEntryId(accountEntryId);
        line.setAccountId(account == null ? null : account.getId());
        line.setEntrySide(side);
        line.setAmount(amount);
        line.setCreateTime(now());
        return line;
    }

    /** 完成一条指令计划：按“规则行 → Owner 行 → 反向行”编行号，校验平衡并构造指令。 */
    void finish(TransactionPostingContext context, PlannedInstruction plan) {
        List<InstructionLine> lines = plan.lines();
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).setLineNo(i + 1);
        }
        checkBalanced(context, plan.type, lines);
        plan.instruction = buildInstruction(context, plan, lines);
        lines.forEach(line -> line.setInstructionId(plan.instruction.getId()));
    }

    /** 每条指令借贷合计必须相等。 */
    void checkBalanced(TransactionPostingContext context, AutoInstructionType type, List<InstructionLine> lines) {
        BigDecimal debit = sum(lines, EntrySide.DEBIT);
        BigDecimal credit = sum(lines, EntrySide.CREDIT);
        if (debit.compareTo(credit) != 0) {
            throw reject(context, type, "指令借贷不平衡 借方 " + debit.toPlainString() + ", 贷方 " + credit.toPlainString());
        }
    }

    /** 某一方向金额合计。 */
    BigDecimal sum(List<InstructionLine> lines, EntrySide side) {
        return lines.stream().filter(line -> line.getEntrySide() == side).map(InstructionLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** VALIDATED 的 ACCOUNT_POSTING 指令：来源为 account 批次（主键、序号），冻结规则版本与输入摘要。 */
    AccountingInstruction buildInstruction(TransactionPostingContext context, PlannedInstruction plan,
        List<InstructionLine> lines) {
        AccountPostingBatch batch = context.getCommand().getBatch();
        Instant now = now();
        AccountingInstruction instruction = new AccountingInstruction();
        instruction.setId(idGenerator.nextId());
        instruction.setLedgerBookId(plan.book.getId());
        instruction.setInstructionType(plan.type.getCode());
        instruction.setOriginType(InstructionOrigin.ACCOUNT_POSTING);
        instruction.setSourceSystem(AccountPostingBatch.SOURCE_SYSTEM);
        instruction.setSourceEventKey(batch.postingKey());
        instruction.setSourceVersion(batch.sequence());
        instruction.setEconomicFactId(context.factId());
        instruction.setAccountingRuleBundleVerId(context.getBundleVer().getId());
        instruction.setInputHash(PeriodCloseContentHash.sha256(inputText(context, plan, lines)));
        instruction.setStatus(InstructionStatus.VALIDATED);
        instruction.setVersion(0L);
        instruction.setCreateTime(now);
        instruction.setUpdateTime(now);
        return instruction;
    }

    /**
     * 输入摘要规范文本
     * <li>首行“规则版本ID|经济结果ID|指令类型|批次ID”，随后按行号“规则行ID|流水ID|账户ID|方向|金额”（缺省为空串）。</li>
     */
    String inputText(TransactionPostingContext context, PlannedInstruction plan, List<InstructionLine> lines) {
        String header = context.getBundleVer().getId() + "|" + context.factId() + "|" + plan.type.getCode() + "|"
            + context.getCommand().getBatch().postingKey();
        String body = lines.stream().map(line -> Objects.toString(line.getAccountingRuleLineId(), "") + "|"
            + Objects.toString(line.getAccountEntryId(), "") + "|" + line.getAccountId() + "|"
            + line.getEntrySide().getCode() + "|" + line.getAmount().stripTrailingZeros().toPlainString())
            .collect(Collectors.joining("\n"));
        return header + "\n" + body;
    }

    /** 保存指令与指令行，以 STANDARD 分录过账；内核把指令迁移为 POSTED。 */
    PostedInstructionView savePosted(PlannedInstruction plan) {
        List<InstructionLine> lines = plan.lines();
        accountingInstructionRepository.insert(plan.instruction);
        instructionLineRepository.insertBatch(lines);
        JournalEntry entry = journalPostingService.post(new JournalPostingContext(plan.instruction, lines,
            plan.period.getId(), plan.postingDate, JournalEntryType.STANDARD));
        return new PostedInstructionView(plan.instruction.getId(), plan.book.getId(), plan.type, entry.getId(),
            entry.getJournalSequence());
    }

    /** 带计费事实、入账批次、指令类型与原因的业务拒绝。 */
    BizCheckFailException reject(TransactionPostingContext context, AutoInstructionType type, String reason) {
        return new BizCheckFailException("控制科目过账拒绝: 计费事实 " + context.factId() + ", 入账批次 "
            + context.getCommand().getBatch().postingKey() + ", 指令类型 " + type.getCode() + ", " + reason);
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 解析出的账户核算对象。 */
    static final class Owner {
        private final LedgerOwnerType type;
        private final String id;

        /** 核算对象类型与稳定标识。 */
        Owner(LedgerOwnerType type, String id) {
            this.type = type;
            this.id = id;
        }
    }

    /** 单个指令类型的生成计划：目标账簿、期间、过账日期，以及按来源分组的指令行。 */
    static final class PlannedInstruction {
        private final AutoInstructionType type;
        private final LedgerBook book;
        private final AccountingPeriod period;
        private final LocalDate postingDate;
        private final List<InstructionLine> ruleLines = new ArrayList<>();
        private final List<InstructionLine> ownerLines = new ArrayList<>();
        private final List<InstructionLine> reversalLines = new ArrayList<>();
        private AccountingInstruction instruction;

        /** 指令与指令行在规则解析后填充。 */
        PlannedInstruction(AutoInstructionType type, LedgerBook book, AccountingPeriod period, LocalDate postingDate) {
            this.type = type;
            this.book = book;
            this.period = period;
            this.postingDate = postingDate;
        }

        /** 追加规则行。 */
        void addRuleLine(InstructionLine line) {
            ruleLines.add(line);
        }

        /** 追加 Owner 控制科目行。 */
        void addOwnerLine(InstructionLine line) {
            ownerLines.add(line);
        }

        /** 追加被替代事实的反向规则行。 */
        void addReversalLine(InstructionLine line) {
            reversalLines.add(line);
        }

        /** 按“规则行 → Owner 行 → 反向行”顺序的全部指令行（每次返回同一组对象）。 */
        List<InstructionLine> lines() {
            List<InstructionLine> all = new ArrayList<>(ruleLines);
            all.addAll(ownerLines);
            all.addAll(reversalLines);
            return all;
        }
    }
}
