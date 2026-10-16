package com.paytech.finance.accounting.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.finance.accounting.core.dal.dataobject.Account;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingInstruction;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingPeriod;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleLine;
import com.paytech.finance.accounting.core.dal.dataobject.ChannelAccount;
import com.paytech.finance.accounting.core.dal.dataobject.ChartAccount;
import com.paytech.finance.accounting.core.dal.dataobject.FiscalCalendar;
import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;
import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;
import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomicComponent;
import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomics;
import com.paytech.finance.accounting.core.enums.AccountStatus;
import com.paytech.finance.accounting.core.enums.AmountSource;
import com.paytech.finance.accounting.core.enums.AutoInstructionType;
import com.paytech.finance.accounting.core.enums.BookStatus;
import com.paytech.finance.accounting.core.enums.EconomicsType;
import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.core.enums.InstructionOrigin;
import com.paytech.finance.accounting.core.enums.InstructionStatus;
import com.paytech.finance.accounting.core.enums.JournalEntryType;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.accounting.core.enums.ReferenceDataStatus;
import com.paytech.finance.accounting.core.enums.RuleVersionStatus;
import com.paytech.finance.accounting.domain.AccountIdentityRule;
import com.paytech.finance.accounting.domain.JournalPostingContext;
import com.paytech.finance.accounting.domain.PeriodCloseContentHash;
import com.paytech.finance.accounting.domain.TransactionPostingCommand;
import com.paytech.finance.accounting.domain.TransactionPostingContext;
import com.paytech.finance.accounting.domain.view.PostedInstructionView;
import com.paytech.finance.accounting.domainservice.repository.AccountRepository;
import com.paytech.finance.accounting.domainservice.repository.AccountingInstructionRepository;
import com.paytech.finance.accounting.domainservice.repository.AccountingPeriodRepository;
import com.paytech.finance.accounting.domainservice.repository.AccountingRuleBundleRepository;
import com.paytech.finance.accounting.domainservice.repository.AccountingRuleBundleVerRepository;
import com.paytech.finance.accounting.domainservice.repository.AccountingRuleLineRepository;
import com.paytech.finance.accounting.domainservice.repository.ChannelAccountRepository;
import com.paytech.finance.accounting.domainservice.repository.ChartAccountRepository;
import com.paytech.finance.accounting.domainservice.repository.FiscalCalendarRepository;
import com.paytech.finance.accounting.domainservice.repository.InstructionLineRepository;
import com.paytech.finance.accounting.domainservice.repository.JournalEntryRepository;
import com.paytech.finance.accounting.domainservice.repository.LedgerBookRepository;
import com.paytech.finance.accounting.domainservice.repository.TransactionEconomicComponentRepository;
import com.paytech.finance.accounting.domainservice.repository.TransactionEconomicsRepository;
import com.paytech.finance.accounting.domainservice.service.JournalPostingService;
import com.paytech.finance.accounting.domainservice.service.TransactionPostingService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/**
 * 交易自动记账引擎
 * <li>顺序：幂等返回 → 读取并校验经济结果 → 选定生效规则版本 → 逐指令类型生成（取金额、解析账簿/期间/账户、校验平衡）→ 按账簿主键升序保存并过账。</li>
 * <li>账户不存在时按身份矩阵在调用方事务内创建；渠道清算账户配置行必须预先存在且 ACTIVE，不在此创建。</li>
 * <li>过账复用记账内核，指令由内核迁移为 POSTED；锁顺序沿用“期间 → 账簿序号 → 账户升序”。</li>
 */
@Service
@AllArgsConstructor
public class TransactionPostingServiceImpl implements TransactionPostingService {
    /** 交易自动记账使用的规则集代码。 */
    static final String STANDARD_TRANSACTION_BUNDLE = "STANDARD_TRANSACTION";

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AccountRepository accountRepository;
    private final LedgerBookRepository ledgerBookRepository;
    private final JournalPostingService journalPostingService;
    private final ChartAccountRepository chartAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final ChannelAccountRepository channelAccountRepository;
    private final FiscalCalendarRepository fiscalCalendarRepository;
    private final InstructionLineRepository instructionLineRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountingRuleLineRepository accountingRuleLineRepository;
    private final TransactionEconomicsRepository transactionEconomicsRepository;
    private final AccountingRuleBundleRepository accountingRuleBundleRepository;
    private final AccountingInstructionRepository accountingInstructionRepository;
    private final AccountingRuleBundleVerRepository accountingRuleBundleVerRepository;
    private final TransactionEconomicComponentRepository transactionEconomicComponentRepository;

    /** 已有 AUTO 指令时直接返回；否则生成全部非空指令并按账簿主键升序过账。 */
    @Override
    public List<PostedInstructionView> post(TransactionPostingCommand command) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("交易自动记账必须在调用方事务内执行");
        }
        List<AccountingInstruction> existing =
            accountingInstructionRepository.findByEconomics(command.getTransactionEconomicsId());
        if (!existing.isEmpty()) {
            return existing.stream().map(this::existingView).toList();
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

    /** 读取经济结果与组件，校验类型、成功时间与 Partner 一致性，并选定生效规则版本。 */
    TransactionPostingContext loadContext(TransactionPostingCommand command) {
        long economicsId = command.getTransactionEconomicsId();
        TransactionEconomics economics = transactionEconomicsRepository.findById(economicsId);
        if (economics == null) {
            throw new BizCheckFailException("交易经济结果不存在: " + economicsId);
        }
        if (economics.getEconomicsType() != EconomicsType.PAYMENT) {
            throw new BizCheckFailException("交易经济结果不是 PAYMENT: " + economicsId + ", 类型 "
                + economics.getEconomicsType().getCode());
        }
        if (economics.getPaymentSucceededTime() == null) {
            throw new BizCheckFailException("交易经济结果缺少支付成功时间: " + economicsId);
        }
        List<TransactionEconomicComponent> components =
            transactionEconomicComponentRepository.findByEconomics(economicsId);
        checkPartnerConsistency(economics, components);
        AccountingRuleBundleVer ver = effectiveBundleVer(economics);
        return new TransactionPostingContext(command, economics, components, ver,
            accountingRuleLineRepository.findByVersion(ver.getId()));
    }

    /**
     * Partner 一致性
     * <li>local_partner_id、partner_agreement_oe_id、finance_entity_id 全有或全无。</li>
     * <li>有 Partner 时必须存在 PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 组件，且服务费 party_id 等于 finance_entity_id；无 Partner 时两者都不得存在。</li>
     */
    void checkPartnerConsistency(TransactionEconomics economics, List<TransactionEconomicComponent> components) {
        long present = Stream.of(economics.getLocalPartnerId(), economics.getPartnerAgreementOeId(),
            economics.getFinanceEntityId()).filter(Objects::nonNull).count();
        if (present != 0 && present != 3) {
            throw new BizCheckFailException("交易经济结果 Partner 三元组不完整: " + economics.getId());
        }
        boolean hasPartner = present == 3;
        for (AmountSource source : List.of(AmountSource.PARTNER_SHARE, AmountSource.PLATFORM_SERVICE_CHARGE)) {
            boolean exists = components.stream().anyMatch(c -> source.getCode().equals(c.getComponentType()));
            if (exists != hasPartner) {
                throw new BizCheckFailException("交易经济结果 Partner 组件不一致: " + economics.getId() + ", 组件 "
                    + source.getCode() + (hasPartner ? " 缺失" : " 不应存在"));
            }
        }
        if (hasPartner) {
            checkServiceChargeParty(economics, components);
        }
    }

    /** 平台服务费组件的 party_id 必须逐值等于 Header finance_entity_id。 */
    void checkServiceChargeParty(TransactionEconomics economics, List<TransactionEconomicComponent> components) {
        String financeEntityId = String.valueOf(economics.getFinanceEntityId());
        boolean mismatch = components.stream()
            .filter(c -> AmountSource.PLATFORM_SERVICE_CHARGE.getCode().equals(c.getComponentType()))
            .anyMatch(c -> !financeEntityId.equals(c.getPartyId()));
        if (mismatch) {
            throw new BizCheckFailException("交易经济结果平台服务费归属与 Finance Entity 不一致: " + economics.getId()
                + ", Finance Entity " + financeEntityId);
        }
    }

    /** 规则集当前版本必须 ACTIVE，且生效区间 [effective_from, effective_to) 覆盖支付成功时间。 */
    AccountingRuleBundleVer effectiveBundleVer(TransactionEconomics economics) {
        AccountingRuleBundle bundle = accountingRuleBundleRepository.findByCode(STANDARD_TRANSACTION_BUNDLE);
        if (bundle == null || bundle.getStatus() != ReferenceDataStatus.ACTIVE || bundle.getCurrentVerId() == null) {
            throw new BizCheckFailException("无可用自动记账规则集: " + STANDARD_TRANSACTION_BUNDLE + ", 交易经济结果 "
                + economics.getId());
        }
        AccountingRuleBundleVer ver = accountingRuleBundleVerRepository.findById(bundle.getCurrentVerId());
        Instant at = economics.getPaymentSucceededTime();
        boolean effective = ver != null && ver.getStatus() == RuleVersionStatus.ACTIVE
            && !ver.getEffectiveFrom().isAfter(at) && (ver.getEffectiveTo() == null || at.isBefore(ver.getEffectiveTo()));
        if (!effective) {
            throw new BizCheckFailException("支付成功时间无生效的自动记账规则版本: 交易经济结果 " + economics.getId()
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
        LocalDate postingDate = LocalDate.ofInstant(context.getEconomics().getPaymentSucceededTime(),
            ZoneId.of(calendar.getTimezone()));
        AccountingPeriod period = findOpenPeriod(context, type, book, calendar, postingDate);
        PlannedInstruction plan = new PlannedInstruction(type, book, period, postingDate);
        for (AccountingRuleLine rule : rules) {
            Account account = resolveAccount(context, type, book, rule);
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
        TransactionEconomics economics = context.getEconomics();
        boolean platform = type == AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM;
        LedgerEntityType entityType = platform ? LedgerEntityType.FINANCE_ENTITY : LedgerEntityType.OPERATING_ENTITY;
        String entityId = platform ? Objects.toString(economics.getFinanceEntityId(), null) : economics.getEntityNo();
        LedgerBook book = entityId == null ? null
            : ledgerBookRepository.findByEntity(entityType, entityId, economics.getCurrency());
        if (book == null || book.getBookStatus() != BookStatus.OPEN) {
            throw reject(context, type, "账簿不存在或未开放 " + entityType.getCode() + "/" + entityId + "/"
                + economics.getCurrency());
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

    /** 按规则行科目、Owner Role 与余额桶解析唯一账户；不存在时在当前事务内创建零余额 ACTIVE 账户。 */
    Account resolveAccount(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        AccountingRuleLine rule) {
        ChartAccount chart = chartAccountRepository.findByChartAndCode(book.getChartId(), rule.getAccountCode());
        if (chart == null || !Boolean.TRUE.equals(chart.getPostingAllowed())) {
            throw reject(context, type, "科目不存在或不可过账 " + rule.getAccountCode() + ", 账簿 " + book.getId());
        }
        Owner owner = resolveOwner(context, type, book, rule);
        AccountIdentityRule.checkAccountIdentity(rule.getAccountCode(), owner.type, rule.getBalanceBucket());
        Account account = accountRepository.findByIdentity(book.getId(), chart.getId(), owner.type, owner.id,
            rule.getBalanceBucket());
        if (account != null) {
            return account;
        }
        account = newAccount(book, chart, owner, rule);
        accountRepository.insert(account);
        return account;
    }

    /** 按固定解释器把 Owner Role 解析为账户 owner_type / owner_id。 */
    Owner resolveOwner(TransactionPostingContext context, AutoInstructionType type, LedgerBook book,
        AccountingRuleLine rule) {
        TransactionEconomics economics = context.getEconomics();
        return switch (rule.getOwnerRole()) {
            case BOOK_ENTITY -> new Owner(book.getEntityType() == LedgerEntityType.OPERATING_ENTITY
                ? OwnerType.OPERATING_ENTITY : OwnerType.FINANCE_ENTITY, book.getEntityId());
            case CLIENT -> new Owner(OwnerType.CLIENT, String.valueOf(economics.getClientId()));
            case LOCAL_PARTNER -> new Owner(OwnerType.LOCAL_PARTNER,
                requireId(context, type, economics.getLocalPartnerId(), "local_partner_id"));
            case CHANNEL_ACCOUNT -> new Owner(OwnerType.CHANNEL_ACCOUNT,
                String.valueOf(channelAccount(context, type).getId()));
            case PLATFORM_FINANCE_ENTITY -> new Owner(OwnerType.FINANCE_ENTITY,
                requireId(context, type, economics.getFinanceEntityId(), "finance_entity_id"));
            case TRANSACTION_OE -> new Owner(OwnerType.OPERATING_ENTITY, economics.getEntityNo());
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

    /** 按命令 MID 与经济结果币种命中 ACTIVE 渠道清算账户，且必须属于交易 OE；不存在即拒绝，不自动创建。 */
    ChannelAccount channelAccount(TransactionPostingContext context, AutoInstructionType type) {
        if (context.getChannelAccount() != null) {
            return context.getChannelAccount();
        }
        TransactionEconomics economics = context.getEconomics();
        long midId = context.getCommand().getMidId();
        ChannelAccount account = channelAccountRepository.findActiveByMid(midId, economics.getCurrency());
        if (account == null || !economics.getEntityNo().equals(account.getEntityNo())) {
            throw reject(context, type, "渠道清算账户不存在、未激活或不属于交易经营主体: MID " + midId + ", 币种 "
                + economics.getCurrency() + ", 经营主体 " + economics.getEntityNo());
        }
        context.setChannelAccount(account);
        return account;
    }

    /** SELLER_TAX 组件必须指向唯一且非空的税务机关。 */
    String taxAuthority(TransactionPostingContext context, AutoInstructionType type) {
        List<String> parties = context.componentsOf(AmountSource.SELLER_TAX.getCode()).stream()
            .map(TransactionEconomicComponent::getPartyId).distinct().toList();
        if (parties.size() != 1 || parties.get(0) == null) {
            throw reject(context, type, "SELLER_TAX 组件税务机关缺失或不唯一 " + parties);
        }
        return parties.get(0);
    }

    /** 零余额 ACTIVE 账户，正常方向从科目复制。 */
    Account newAccount(LedgerBook book, ChartAccount chart, Owner owner, AccountingRuleLine rule) {
        Instant now = now();
        Account account = new Account();
        account.setId(idGenerator.nextId());
        account.setLedgerBookId(book.getId());
        account.setChartAccountId(chart.getId());
        account.setOwnerType(owner.type);
        account.setOwnerId(owner.id);
        account.setBalanceBucket(rule.getBalanceBucket());
        account.setNormalSide(chart.getNormalSide());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setDebitTotal(BigDecimal.ZERO);
        account.setCreditTotal(BigDecimal.ZERO);
        account.setNetBalance(BigDecimal.ZERO);
        account.setLastJournalSequence(0L);
        account.setBalanceVersion(0L);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return account;
    }

    /** 引用规则行的指令行；指令主键在指令构造后回填。 */
    InstructionLine buildLine(int lineNo, AccountingRuleLine rule, Account account, BigDecimal amount) {
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

    /** VALIDATED 的 AUTO 指令：来源 Tuple 取经济结果，冻结规则版本与输入摘要。 */
    AccountingInstruction buildInstruction(TransactionPostingContext context, PlannedInstruction plan) {
        TransactionEconomics economics = context.getEconomics();
        Instant now = now();
        AccountingInstruction instruction = new AccountingInstruction();
        instruction.setId(idGenerator.nextId());
        instruction.setLedgerBookId(plan.book.getId());
        instruction.setInstructionType(plan.type.getCode());
        instruction.setOriginType(InstructionOrigin.AUTO);
        instruction.setSourceSystem(economics.getSourceSystem());
        instruction.setSourceEventKey(economics.getSourceEventKey());
        instruction.setSourceVersion(economics.getSourceVersion());
        instruction.setSourcePayloadHash(economics.getSourcePayloadHash());
        instruction.setTransactionEconomicsId(economics.getId());
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
        String header = context.getBundleVer().getId() + "|" + context.economicsId() + "|" + plan.type.getCode();
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
        return new BizCheckFailException("交易自动记账拒绝: 交易经济结果 " + context.economicsId() + ", 指令类型 "
            + type.getCode() + ", " + reason);
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
