package com.paytech.ledger.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.account.api.enums.OwnerRole;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.AutoInstructionType;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.domain.JournalPostingContext;
import com.paytech.ledger.domain.PeriodCloseContentHash;
import com.paytech.ledger.domain.TransactionPostingCommand;
import com.paytech.ledger.domain.TransactionPostingContext;
import com.paytech.ledger.domain.view.PostedInstructionView;
import com.paytech.ledger.domainservice.repository.AccountingInstructionRepository;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleRepository;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleVerRepository;
import com.paytech.ledger.domainservice.repository.AccountingRuleLineRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
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
 * 交易自动记账引擎
 * <li>顺序：幂等返回 → 断言经济事实载荷完整并校验类型与成功时间 → 选定生效规则版本 → 逐指令类型生成（取金额、解析账簿/期间/账户、校验平衡）→ 按账簿主键升序保存并过账。</li>
 * <li>人工 / 成本调整事实（第 11b 步）：按 STANDARD_ADJUSTMENT 规则集，以明细行的费用类型、付方 / 收方角色与借贷方向匹配规则行，生成一条交易 OE
 * 账簿的指令；规则行是初始化 DML，代码不写死科目。</li>
 * <li>输入驱动：经济事实与渠道清算账户主键由命令载荷给出，事务内只读写 ledger 本库表；Partner 登记不变量由 billing 在登记时校验。</li>
 * <li>账户不存在时按身份矩阵在调用方事务内创建科目账户身份（ledger 本库）。</li>
 * <li>过账复用记账内核，指令由内核迁移为 POSTED；锁顺序为“期间 → 账簿序号”，不锁账户、不写余额（design/04 §4）。</li>
 * <li>生产调用方是 EconomicFactRecorded 消费 Handler；Owner 余额由 account 服务独立消费同一事实维护。</li>
 */
@Service
@AllArgsConstructor
public class TransactionPostingServiceImpl implements TransactionPostingService {
    /** 交易自动记账使用的规则集代码。 */
    static final String STANDARD_TRANSACTION_BUNDLE = "STANDARD_TRANSACTION";

    /** 人工 / 成本调整经济事实使用的规则集代码（第 11b 步）。 */
    static final String STANDARD_ADJUSTMENT_BUNDLE = "STANDARD_ADJUSTMENT";

    /** 调整类经济事实类型 → 指令类型。 */
    static final Map<String, AutoInstructionType> ADJUSTMENT_INSTRUCTIONS = Map.of(
        EconomicsType.MANUAL_ADJUSTMENT.getCode(), AutoInstructionType.ECONOMIC_ADJUSTMENT,
        EconomicsType.COST_ADJUSTMENT.getCode(), AutoInstructionType.CHANNEL_COST_ADJUSTMENT);

    /** 明细行付方 / 收方角色 → 规则行核算对象角色；未列出的角色（PLATFORM、CONSUMER）没有记账规则。 */
    static final Map<String, OwnerRole> PARTY_OWNER_ROLES = Map.of(
        FactPartyRole.CLIENT.getCode(), OwnerRole.CLIENT,
        FactPartyRole.LOCAL_PARTNER.getCode(), OwnerRole.LOCAL_PARTNER,
        FactPartyRole.CHANNEL.getCode(), OwnerRole.CHANNEL_ACCOUNT,
        FactPartyRole.OPERATING_ENTITY.getCode(), OwnerRole.TRANSACTION_OE,
        FactPartyRole.FINANCE_ENTITY.getCode(), OwnerRole.PLATFORM_FINANCE_ENTITY,
        FactPartyRole.TAX_AUTHORITY.getCode(), OwnerRole.TAX_AUTHORITY);

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBookRepository ledgerBookRepository;
    private final JournalPostingService journalPostingService;
    private final ChartAccountRepository chartAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final FiscalCalendarRepository fiscalCalendarRepository;
    private final InstructionLineRepository instructionLineRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountingRuleLineRepository accountingRuleLineRepository;
    private final AccountingRuleBundleRepository accountingRuleBundleRepository;
    private final AccountingInstructionRepository accountingInstructionRepository;
    private final AccountingRuleBundleVerRepository accountingRuleBundleVerRepository;

    /** 已有 AUTO 指令时直接返回；否则生成全部非空指令并按账簿主键升序过账。 */
    @Override
    public List<PostedInstructionView> post(TransactionPostingCommand command) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("交易自动记账必须在调用方事务内执行");
        }
        checkFactComplete(command.getFact());
        List<AccountingInstruction> existing =
            accountingInstructionRepository.findByEconomicFact(command.getFact().getFactId());
        if (!existing.isEmpty()) {
            return existing.stream().map(this::existingView).toList();
        }
        if (ADJUSTMENT_INSTRUCTIONS.containsKey(command.getFact().getFactType())) {
            return List.of(savePosted(planAdjustment(loadAdjustmentContext(command))));
        }
        TransactionPostingContext context = loadContext(command);
        List<PlannedInstruction> planned = new ArrayList<>();
        for (AutoInstructionType type : AutoInstructionType.values()) {
            PlannedInstruction instruction = planInstruction(context, type);
            if (instruction != null) {
                planned.add(instruction);
            }
        }
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

    /** 按稳定编码解析指令类型；非交易自动记账类型返回空。 */
    AutoInstructionType instructionType(String code) {
        for (AutoInstructionType type : AutoInstructionType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 经济事实载荷完整性断言（DTO 完整性；业务不变量由 billing 负责）
     * <li>头主键、类型、来源 Tuple、Client、OE、币种、总额与明细行集合必须存在；每行必须有行号、组件类型与金额。</li>
     */
    void checkFactComplete(EconomicFactPayload fact) {
        if (fact == null) {
            throw new BizCheckFailException("交易经济结果载荷缺失");
        }
        Long factId = fact.getFactId();
        requireField(factId, factId, "fact_id");
        requireField(factId, fact.getFactType(), "fact_type");
        requireField(factId, fact.getSourceSystem(), "source_system");
        requireField(factId, fact.getSourceEventKey(), "source_event_key");
        requireField(factId, fact.getSourceVersion(), "source_version");
        requireField(factId, fact.getSourcePayloadHash(), "source_payload_hash");
        requireField(factId, fact.getClientId(), "client_id");
        requireField(factId, fact.getEntityNo(), "entity_no");
        requireField(factId, fact.getCurrency(), "currency");
        requireField(factId, fact.getGrossAmount(), "gross_amount");
        requireField(factId, fact.getLines(), "lines");
        for (EconomicFactLinePayload line : fact.getLines()) {
            requireField(factId, line, "line");
            requireField(factId, line.getLineNo(), "line_no");
            requireField(factId, line.getComponentType(), "component_type");
            requireField(factId, line.getPostedAmount(), "posted_amount");
        }
    }

    /** 载荷必填字段缺失时业务拒绝。 */
    void requireField(Long factId, Object value, String field) {
        if (value == null) {
            throw new BizCheckFailException("交易经济结果载荷不完整: " + factId + ", 缺少 " + field);
        }
    }

    /** 校验经济事实类型与成功时间，并选定生效规则版本；明细行按行号升序。 */
    TransactionPostingContext loadContext(TransactionPostingCommand command) {
        EconomicFactPayload fact = command.getFact();
        long factId = fact.getFactId();
        if (!EconomicsType.PAYMENT.getCode().equals(fact.getFactType())) {
            throw new BizCheckFailException("交易经济结果不是 PAYMENT、MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT，没有记账规则: "
                + factId + ", 类型 " + fact.getFactType());
        }
        if (fact.getPaymentSucceededTime() == null) {
            throw new BizCheckFailException("交易经济结果缺少支付成功时间: " + factId);
        }
        List<EconomicFactLinePayload> lines = fact.getLines().stream()
            .sorted(Comparator.comparing(EconomicFactLinePayload::getLineNo)).toList();
        AccountingRuleBundleVer ver = effectiveBundleVer(fact);
        return new TransactionPostingContext(command, fact, lines, ver,
            accountingRuleLineRepository.findByVersion(ver.getId()));
    }

    /** 规则集当前版本必须 ACTIVE，且生效区间 [effective_from, effective_to) 覆盖支付成功时间。 */
    AccountingRuleBundleVer effectiveBundleVer(EconomicFactPayload fact) {
        return effectiveBundleVer(fact, STANDARD_TRANSACTION_BUNDLE, fact.getPaymentSucceededTime());
    }

    /** 指定规则集的当前版本必须 ACTIVE，且生效区间 [effective_from, effective_to) 覆盖给定时点。 */
    AccountingRuleBundleVer effectiveBundleVer(EconomicFactPayload fact, String bundleCode, Instant at) {
        AccountingRuleBundle bundle = accountingRuleBundleRepository.findByCode(bundleCode);
        if (bundle == null || bundle.getStatus() != ReferenceDataStatus.ACTIVE || bundle.getCurrentVerId() == null) {
            throw new BizCheckFailException("无可用自动记账规则集: " + bundleCode + ", 交易经济结果 "
                + fact.getFactId());
        }
        AccountingRuleBundleVer ver = accountingRuleBundleVerRepository.findById(bundle.getCurrentVerId());
        boolean effective = ver != null && ver.getStatus() == RuleVersionStatus.ACTIVE
            && !ver.getEffectiveFrom().isAfter(at) && (ver.getEffectiveTo() == null || at.isBefore(ver.getEffectiveTo()));
        if (!effective) {
            throw new BizCheckFailException("支付成功时间无生效的自动记账规则版本: 交易经济结果 " + fact.getFactId()
                + ", 当前版本 " + bundle.getCurrentVerId() + ", 成功时间 " + at);
        }
        return ver;
    }

    /**
     * 生成一个指令类型的计划
     *
     * @return 没有任何有效规则行时为空，不生成该指令
     */
    PlannedInstruction planInstruction(TransactionPostingContext context, AutoInstructionType type) {
        List<AccountingRuleLine> rules = applicableRules(context, type);
        if (rules.isEmpty()) {
            return null;
        }
        LedgerBook book = findOpenBook(context, type);
        FiscalCalendar calendar = findCalendar(context, type, book);
        LocalDate postingDate = LocalDate.ofInstant(context.getFact().getPaymentSucceededTime(),
            ZoneId.of(calendar.getTimezone()));
        AccountingPeriod period = findOpenPeriod(context, type, book, calendar, postingDate);
        PlannedInstruction plan = new PlannedInstruction(type, book, period, postingDate);
        for (AccountingRuleLine rule : rules) {
            LedgerAccount account = resolveAccount(context, type, book, rule);
            plan.lines.add(buildLine(plan.lines.size() + 1, rule, account, context.amountOf(rule.getAmountSourceCode())));
        }
        checkBalanced(context, type, plan.lines);
        plan.instruction = buildInstruction(context, plan);
        plan.lines.forEach(line -> line.setInstructionId(plan.instruction.getId()));
        return plan;
    }

    /**
     * 取金额来源后保留的规则行
     * <li>来源缺失：必需行拒绝，非必需行跳过；来源金额为零：该行跳过（同来源各行金额相同，因此整体跳过）；负数拒绝。</li>
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

    /** 指令类型的目标账簿必须存在且 OPEN：平台侧服务费入 Finance Entity 账簿，其余入交易 OE 账簿。 */
    LedgerBook findOpenBook(TransactionPostingContext context, AutoInstructionType type) {
        EconomicFactPayload fact = context.getFact();
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

    /** 过账日（支付成功时间在日历时区的日期）所在期间必须存在且 OPEN。 */
    AccountingPeriod findOpenPeriod(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        FiscalCalendar calendar, LocalDate postingDate) {
        AccountingPeriod period = accountingPeriodRepository.findByCalendarAndDate(calendar.getId(), postingDate);
        if (period == null || period.getStatus() != PeriodStatus.OPEN) {
            throw reject(context, type, "过账日期所在会计期间不存在或未开放 " + postingDate + ", 账簿 " + book.getId()
                + (period == null ? "" : ", 期间 " + period.getId() + " 状态 " + period.getStatus().getCode()));
        }
        return period;
    }

    /** 按规则行科目、Owner Role 与余额桶解析唯一账户；不存在时在当前事务内创建 ACTIVE 账户身份（只写 ledger 本库）。 */
    LedgerAccount resolveAccount(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        AccountingRuleLine rule) {
        return resolveAccount(context, type, book, rule, resolveOwner(context, type, book, rule));
    }

    /** 按规则行科目、给定核算对象与余额桶解析唯一账户；不存在时在当前事务内创建 ACTIVE 账户身份。 */
    LedgerAccount resolveAccount(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        AccountingRuleLine rule, Owner owner) {
        ChartAccount chart = chartAccountRepository.findByChartAndCode(book.getChartId(), rule.getAccountCode());
        if (chart == null || !Boolean.TRUE.equals(chart.getPostingAllowed())) {
            throw reject(context, type, "科目不存在或不可过账 " + rule.getAccountCode() + ", 账簿 " + book.getId());
        }
        AccountIdentityRule.checkAccountIdentity(rule.getAccountCode(), owner.type, rule.getBalanceBucket());
        LedgerAccount account = ledgerAccountRepository.findByIdentity(book.getId(), chart.getId(), owner.type, owner.id,
            rule.getBalanceBucket());
        if (account != null) {
            return account;
        }
        account = newAccount(book, chart, owner, rule);
        ledgerAccountRepository.insertConcurrently(account);
        return account;
    }

    /** 按固定解释器把 Owner Role 解析为账户 owner_type / owner_id。 */
    Owner resolveOwner(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        AccountingRuleLine rule) {
        EconomicFactPayload fact = context.getFact();
        return switch (rule.getOwnerRole()) {
            case BOOK_ENTITY -> new Owner(book.getEntityType() == LedgerEntityType.OPERATING_ENTITY
                ? OwnerType.OPERATING_ENTITY : OwnerType.FINANCE_ENTITY, book.getEntityId());
            case CLIENT -> new Owner(OwnerType.CLIENT, String.valueOf(fact.getClientId()));
            case LOCAL_PARTNER -> new Owner(OwnerType.LOCAL_PARTNER,
                requireId(context, type, fact.getLocalPartnerId(), "local_partner_id"));
            case CHANNEL_ACCOUNT -> new Owner(OwnerType.CHANNEL_ACCOUNT,
                requireId(context, type, fact.getChannelAccountId(), "channel_account_id"));
            case PLATFORM_FINANCE_ENTITY -> new Owner(OwnerType.FINANCE_ENTITY,
                requireId(context, type, fact.getFinanceEntityId(), "finance_entity_id"));
            case TRANSACTION_OE -> new Owner(OwnerType.OPERATING_ENTITY, fact.getEntityNo());
            case TAX_AUTHORITY -> new Owner(OwnerType.TAX_AUTHORITY, taxAuthority(context, type));
        };
    }

    /** 可空的 Header 数字标识必须存在，返回十进制字符串。 */
    String requireId(TransactionPostingContext context, AutoInstructionType type, Long id, String field) {
        if (id == null) {
            throw reject(context, type, "缺少 " + field);
        }
        return String.valueOf(id);
    }

    /** SELLER_TAX 组件必须指向唯一且非空的税务机关。 */
    String taxAuthority(TransactionPostingContext context, AutoInstructionType type) {
        List<String> parties = context.linesOf(AmountSource.SELLER_TAX.getCode()).stream()
            .map(EconomicFactLinePayload::getPartyId).distinct().toList();
        if (parties.size() != 1 || parties.get(0) == null) {
            throw reject(context, type, "SELLER_TAX 组件税务机关缺失或不唯一 " + parties);
        }
        return parties.get(0);
    }

    /** ACTIVE 账户身份，正常方向从科目复制；不保存余额。 */
    LedgerAccount newAccount(LedgerBook book, ChartAccount chart, Owner owner, AccountingRuleLine rule) {
        Instant now = now();
        LedgerAccount account = new LedgerAccount();
        account.setId(idGenerator.nextId());
        account.setLedgerBookId(book.getId());
        account.setChartAccountId(chart.getId());
        account.setOwnerType(owner.type);
        account.setOwnerId(owner.id);
        account.setBalanceBucket(rule.getBalanceBucket());
        account.setNormalSide(chart.getNormalSide());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return account;
    }

    /** 引用规则行的指令行；指令主键在指令构造后回填。 */
    InstructionLine buildLine(int lineNo, AccountingRuleLine rule, LedgerAccount account, BigDecimal amount) {
        InstructionLine line = new InstructionLine();
        line.setId(idGenerator.nextId());
        line.setLineNo(lineNo);
        line.setAccountingRuleLineId(rule.getId());
        line.setAccountId(account.getId());
        line.setEntrySide(rule.getEntrySide());
        line.setAmount(amount);
        line.setCreateTime(now());
        return line;
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

    /** VALIDATED 的 AUTO 指令：来源 Tuple 取经济事实头，冻结规则版本与输入摘要。 */
    AccountingInstruction buildInstruction(TransactionPostingContext context, PlannedInstruction plan) {
        EconomicFactPayload fact = context.getFact();
        Instant now = now();
        AccountingInstruction instruction = new AccountingInstruction();
        instruction.setId(idGenerator.nextId());
        instruction.setLedgerBookId(plan.book.getId());
        instruction.setInstructionType(plan.type.getCode());
        instruction.setOriginType(InstructionOrigin.AUTO);
        instruction.setSourceSystem(fact.getSourceSystem());
        instruction.setSourceEventKey(fact.getSourceEventKey());
        instruction.setSourceVersion(fact.getSourceVersion());
        instruction.setSourcePayloadHash(fact.getSourcePayloadHash());
        instruction.setEconomicFactId(fact.getFactId());
        instruction.setAccountingRuleBundleVerId(context.getBundleVer().getId());
        instruction.setInputHash(PeriodCloseContentHash.sha256(inputText(context, plan)));
        instruction.setStatus(InstructionStatus.VALIDATED);
        instruction.setVersion(0L);
        instruction.setCreateTime(now);
        instruction.setUpdateTime(now);
        return instruction;
    }

    /** 输入摘要规范文本：首行“规则版本ID|经济结果ID|指令类型”，随后按行号“规则行ID|账户ID|方向|金额”。 */
    String inputText(TransactionPostingContext context, PlannedInstruction plan) {
        String header = context.getBundleVer().getId() + "|" + context.factId() + "|" + plan.type.getCode();
        String lines = plan.lines.stream().map(line -> line.getAccountingRuleLineId() + "|" + line.getAccountId() + "|"
            + line.getEntrySide().getCode() + "|" + line.getAmount().stripTrailingZeros().toPlainString())
            .collect(Collectors.joining("\n"));
        return header + "\n" + lines;
    }

    /** 保存指令与指令行，以 STANDARD 分录过账；内核把指令迁移为 POSTED。 */
    PostedInstructionView savePosted(PlannedInstruction plan) {
        accountingInstructionRepository.insert(plan.instruction);
        instructionLineRepository.insertBatch(plan.lines);
        JournalEntry entry = journalPostingService.post(new JournalPostingContext(plan.instruction, plan.lines,
            plan.period.getId(), plan.postingDate, JournalEntryType.STANDARD));
        return new PostedInstructionView(plan.instruction.getId(), plan.book.getId(), plan.type, entry.getId(),
            entry.getJournalSequence());
    }

    /** 带经济结果、指令类型与缺失项的业务拒绝。 */
    BizCheckFailException reject(TransactionPostingContext context, AutoInstructionType type, String reason) {
        return new BizCheckFailException("交易自动记账拒绝: 交易经济结果 " + context.factId() + ", 指令类型 "
            + type.getCode() + ", " + reason);
    }

    /**
     * 调整类事实的规则上下文：登记（批准）时间必须存在，并选定 STANDARD_ADJUSTMENT 在该时点生效的版本；明细行按行号升序
     */
    TransactionPostingContext loadAdjustmentContext(TransactionPostingCommand command) {
        EconomicFactPayload fact = command.getFact();
        if (fact.getRecordedTime() == null) {
            throw new BizCheckFailException("调整经济事实缺少登记时间: " + fact.getFactId());
        }
        List<EconomicFactLinePayload> lines = fact.getLines().stream()
            .sorted(Comparator.comparing(EconomicFactLinePayload::getLineNo)).toList();
        AccountingRuleBundleVer ver = effectiveBundleVer(fact, STANDARD_ADJUSTMENT_BUNDLE, fact.getRecordedTime());
        return new TransactionPostingContext(command, fact, lines, ver,
            accountingRuleLineRepository.findByVersion(ver.getId()));
    }

    /**
     * 调整类事实的指令计划（design/04 §4 第 3 行）：每条明细行的付方记借方、收方记贷方（方向为 REVERSAL 时互换），
     * 按“费用类型 × 角色对应的核算对象角色 × 借贷方向”唯一匹配规则行取科目与余额桶；交易 OE 账簿、登记时间所在 OPEN 期间
     * <li>匹配不到或匹配多条、角色没有记账规则、缺少付方 / 收方、金额为负时整笔拒绝。</li>
     */
    PlannedInstruction planAdjustment(TransactionPostingContext context) {
        AutoInstructionType type = ADJUSTMENT_INSTRUCTIONS.get(context.getFact().getFactType());
        LedgerBook book = findOpenBook(context, type);
        FiscalCalendar calendar = findCalendar(context, type, book);
        LocalDate postingDate = LocalDate.ofInstant(context.getFact().getRecordedTime(), ZoneId.of(calendar.getTimezone()));
        PlannedInstruction plan = new PlannedInstruction(type, book, findOpenPeriod(context, type, book, calendar,
            postingDate), postingDate);
        for (EconomicFactLinePayload line : context.getLines()) {
            if (line.getPayerRole() == null || line.getPayeeRole() == null) {
                throw reject(context, type, "明细行缺少付方或收方: 行 " + line.getLineNo());
            }
            if (line.getPostedAmount().signum() < 0) {
                throw reject(context, type, "明细行金额为负: 行 " + line.getLineNo());
            }
            if (line.getPostedAmount().signum() == 0) {
                continue;
            }
            boolean reversal = FactLineDirection.REVERSAL.getCode().equals(line.getDirection());
            addAdjustmentLine(context, plan, line, line.getPayerRole(), line.getPayerId(),
                reversal ? EntrySide.CREDIT : EntrySide.DEBIT);
            addAdjustmentLine(context, plan, line, line.getPayeeRole(), line.getPayeeId(),
                reversal ? EntrySide.DEBIT : EntrySide.CREDIT);
        }
        if (plan.lines.isEmpty()) {
            throw reject(context, type, "没有可过账的明细行");
        }
        checkBalanced(context, type, plan.lines);
        plan.instruction = buildInstruction(context, plan);
        plan.lines.forEach(instructionLine -> instructionLine.setInstructionId(plan.instruction.getId()));
        return plan;
    }

    /** 一个付方或收方：匹配唯一规则行、解析账户并追加指令行。 */
    void addAdjustmentLine(TransactionPostingContext context, PlannedInstruction plan, EconomicFactLinePayload line,
        String partyRole, String partyId, EntrySide side) {
        OwnerRole ownerRole = PARTY_OWNER_ROLES.get(partyRole);
        if (ownerRole == null) {
            throw reject(context, plan.type, "角色没有记账规则 " + partyRole + ": 行 " + line.getLineNo());
        }
        List<AccountingRuleLine> matched = context.rulesOf(plan.type).stream()
            .filter(rule -> rule.getAmountSourceCode().getCode().equals(line.getComponentType())
                && rule.getOwnerRole() == ownerRole && rule.getEntrySide() == side)
            .toList();
        if (matched.size() != 1) {
            throw reject(context, plan.type, "费用类型 " + line.getComponentType() + " × 角色 " + partyRole + " × "
                + side.getCode() + " 匹配 " + matched.size() + " 条规则行: 行 " + line.getLineNo());
        }
        AccountingRuleLine rule = matched.getFirst();
        LedgerAccount account = resolveAccount(context, plan.type, plan.book, rule,
            adjustmentOwner(context, plan.type, plan.book, ownerRole, partyId));
        plan.lines.add(buildLine(plan.lines.size() + 1, rule, account, line.getPostedAmount()));
    }

    /** 调整行的核算对象：渠道取事实头渠道清算账户，交易 OE 取事实头 entityNo，其余取明细行标识。 */
    Owner adjustmentOwner(TransactionPostingContext context, AutoInstructionType type, LedgerBook book, OwnerRole role,
        String partyId) {
        EconomicFactPayload fact = context.getFact();
        Owner owner = switch (role) {
            case CHANNEL_ACCOUNT -> new Owner(OwnerType.CHANNEL_ACCOUNT,
                requireId(context, type, fact.getChannelAccountId(), "channel_account_id"));
            case TRANSACTION_OE -> new Owner(OwnerType.OPERATING_ENTITY, fact.getEntityNo());
            case CLIENT -> new Owner(OwnerType.CLIENT, partyId);
            case LOCAL_PARTNER -> new Owner(OwnerType.LOCAL_PARTNER, partyId);
            case PLATFORM_FINANCE_ENTITY -> new Owner(OwnerType.FINANCE_ENTITY, partyId);
            case TAX_AUTHORITY -> new Owner(OwnerType.TAX_AUTHORITY, partyId);
            case BOOK_ENTITY -> new Owner(OwnerType.OPERATING_ENTITY, book.getEntityId());
        };
        if (owner.id == null) {
            throw reject(context, type, "角色 " + role.getCode() + " 缺少标识");
        }
        AccountIdentityRule.checkOwnerId(owner.type, owner.id);
        return owner;
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 解析出的账户核算对象。 */
    static final class Owner {
        private final OwnerType type;
        private final String id;

        /** 核对对象类型与稳定标识。 */
        Owner(OwnerType type, String id) {
            this.type = type;
            this.id = id;
        }
    }

    /** 单个指令类型的生成计划：目标账簿、期间、过账日期、指令与指令行。 */
    static final class PlannedInstruction {
        private final AutoInstructionType type;
        private final LedgerBook book;
        private final AccountingPeriod period;
        private final List<InstructionLine> lines = new ArrayList<>();
        private final LocalDate postingDate;
        private AccountingInstruction instruction;

        /** 指令行与指令在规则解析后填充。 */
        PlannedInstruction(AutoInstructionType type, LedgerBook book, AccountingPeriod period, LocalDate postingDate) {
            this.type = type;
            this.book = book;
            this.period = period;
            this.postingDate = postingDate;
        }
    }
}
