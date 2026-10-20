package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.AmountSource;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.enums.RuleOwnerRole;
import com.paytech.ledger.core.constant.AccountingCodes;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.domain.AccountingRuleVersionContext;
import com.paytech.ledger.domain.view.RuleVersionView;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleVerRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.ChartOfAccountsRepository;
import com.paytech.ledger.domainservice.service.AccountingRuleService;
import com.paytech.ledger.domainservice.validator.account.AccountIdentityRule;
import com.paytech.ledger.service.processor.request.CreateRuleVersionRequest;

import lombok.AllArgsConstructor;

/**
 * 以完整有序规则行集创建 DRAFT 规则版本，版本号为当前最大值 + 1
 * <li>行集非空；代码均属固定目录（核算对象角色只有平台类，Client / Local Partner 角色已删除）；科目在 STANDARD 科目表存在且可过账，
 * 且不是 Owner 控制科目；Owner Role 解析出的 Owner 类型与科目、余额桶组合符合账户身份矩阵；同一指令类型内行号、行代码唯一。</li>
 * <li>同一规则集已有 DRAFT 或 PENDING_APPROVAL 版本时拒绝；行集随版本一次写入，之后不提供单行追加。</li>
 * <li>与同一规则集的其他写入口共用资源锁并持续等待；事务内锁规则集后复验待处理版本，版本号由唯一键保护。</li>
 */
@Component
@AllArgsConstructor
public class CreateRuleVersionProcessor extends AbstractLockProcessor<CreateRuleVersionRequest, RuleVersionView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AccountingRuleService accountingRuleService;
    private final ChartAccountRepository chartAccountRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AccountingRuleBundleVerRepository accountingRuleBundleVerRepository;

    /** 行集非空、行号为正，同一指令类型内行号与行代码不重复。 */
    @Override
    protected void validate(CreateRuleVersionRequest request) {
        if (request.getLines().isEmpty()) {
            throw new BizCheckFailException("规则行集不能为空: 规则集 " + request.getBundleCode());
        }
        Set<String> lineNos = new HashSet<>();
        Set<String> lineCodes = new HashSet<>();
        for (CreateRuleVersionRequest.Line line : request.getLines()) {
            if (line.getLineNo() <= 0) {
                throw new BizCheckFailException("规则行号必须为正整数: 指令类型 " + line.getInstructionType() + ", 行号 "
                    + line.getLineNo());
            }
            if (!lineNos.add(line.getInstructionType() + "|" + line.getLineNo())) {
                throw new BizCheckFailException("同一指令类型内规则行号重复: 指令类型 " + line.getInstructionType() + ", 行号 "
                    + line.getLineNo());
            }
            if (!lineCodes.add(line.getInstructionType() + "|" + line.getLineCode())) {
                throw new BizCheckFailException("同一指令类型内规则行代码重复: 指令类型 " + line.getInstructionType() + ", 行代码 "
                    + line.getLineCode());
            }
        }
    }

    /** 规则集资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CreateRuleVersionRequest request) {
        return LockCommand.lock(AccountingLocks.accountingRuleBundle(request.getBundleCode()));
    }

    @Override
    protected RuleVersionView processWithinLock(CreateRuleVersionRequest request) {
        AccountingRuleBundle bundle = accountingRuleService.loadBundle(request.getBundleCode());
        checkNoOpenVersion(bundle);
        AccountingRuleVersionContext context = buildContext(bundle, request);
        accountingRuleService.createVersion(context);
        return context.toView();
    }

    /** 同一规则集不能同时存在 DRAFT 或 PENDING_APPROVAL 版本。 */
    void checkNoOpenVersion(AccountingRuleBundle bundle) {
        if (accountingRuleBundleVerRepository.existsOpenVersion(bundle.getId())) {
            throw new BizCheckFailException("规则集已有草稿或待复核版本: " + bundle.getBundleCode());
        }
    }

    /** 构建新版本与其全部规则行。 */
    AccountingRuleVersionContext buildContext(AccountingRuleBundle bundle, CreateRuleVersionRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AccountingRuleBundleVer ver = buildVersion(bundle, request, now);
        ChartOfAccounts chart = getStandardChart();
        List<AccountingRuleLine> lines =
            request.getLines().stream().map(line -> buildLine(ver, chart, line, now)).toList();
        return new AccountingRuleVersionContext(bundle, ver, lines);
    }

    /** DRAFT 版本，版本号为当前最大值 + 1。 */
    AccountingRuleBundleVer buildVersion(AccountingRuleBundle bundle, CreateRuleVersionRequest request, Instant now) {
        AccountingRuleBundleVer ver = new AccountingRuleBundleVer();
        ver.setId(idGenerator.nextId());
        ver.setBundleId(bundle.getId());
        ver.setBundleVer(accountingRuleBundleVerRepository.nextBundleVer(bundle.getId()));
        ver.setEffectiveFrom(request.getEffectiveFrom().truncatedTo(ChronoUnit.MICROS));
        ver.setStatus(RuleVersionStatus.DRAFT);
        ver.setMakerId(request.getMakerId());
        ver.setCreateTime(now);
        ver.setUpdateTime(now);
        return ver;
    }

    /** 规则行科目校验使用的 STANDARD 科目表，必须由初始化数据提供。 */
    ChartOfAccounts getStandardChart() {
        ChartOfAccounts chart = chartOfAccountsRepository.findByChartCode(AccountingCodes.STANDARD_CHART_CODE);
        if (chart == null) {
            throw new ErrorException("标准科目表缺失: " + AccountingCodes.STANDARD_CHART_CODE);
        }
        return chart;
    }

    /** 解析代码并校验科目与账户身份后构建规则行。 */
    AccountingRuleLine buildLine(AccountingRuleBundleVer ver, ChartOfAccounts chart, CreateRuleVersionRequest.Line source,
        Instant now) {
        AccountingRuleLine line = new AccountingRuleLine();
        line.setId(idGenerator.nextId());
        line.setBundleVerId(ver.getId());
        line.setInstructionType(parse(AutoInstructionType.class, "指令类型", source.getInstructionType()));
        line.setLineNo(source.getLineNo());
        line.setLineCode(source.getLineCode());
        line.setAmountSourceCode(parse(AmountSource.class, "金额来源", source.getAmountSourceCode()));
        line.setOwnerRole(parse(RuleOwnerRole.class, "核算对象角色", source.getOwnerRole()));
        line.setAccountCode(source.getAccountCode());
        line.setBalanceBucket(parse(BalanceBucket.class, "余额桶", source.getBalanceBucket()));
        line.setEntrySide(parse(EntrySide.class, "借贷方向", source.getEntrySide()));
        line.setRequiredFlag(source.getRequiredFlag());
        line.setCreateTime(now);
        checkPostingAccount(chart, line);
        checkNotControlAccount(line);
        AccountIdentityRule.checkAccountIdentity(line.getAccountCode(),
            ownerType(line.getOwnerRole(), line.getInstructionType()), line.getBalanceBucket());
        return line;
    }

    /** 科目必须在 STANDARD 科目表中存在、允许过账且未停用。 */
    void checkPostingAccount(ChartOfAccounts chart, AccountingRuleLine line) {
        ChartAccount account = chartAccountRepository.findByChartAndCode(chart.getId(), line.getAccountCode());
        if (account == null || !Boolean.TRUE.equals(account.getPostingAllowed())) {
            throw new BizCheckFailException("科目不存在或不可过账: 科目 " + line.getAccountCode() + ", 指令类型 "
                + line.getInstructionType().getCode() + ", 行号 " + line.getLineNo());
        }
        if (account.getAccountStatus() != ChartAccountStatus.ACTIVE) {
            throw new BizCheckFailException("科目已停用，不能被新规则版本引用: 科目 " + line.getAccountCode() + ", 指令类型 "
                + line.getInstructionType().getCode() + ", 行号 " + line.getLineNo());
        }
    }

    /** Owner 控制科目只由 account 入账批次的流水生成，规则行不得引用。 */
    void checkNotControlAccount(AccountingRuleLine line) {
        if (AccountIdentityRule.isControlAccount(line.getAccountCode())) {
            throw new BizCheckFailException("规则行不得引用 Owner 控制科目，控制科目行由 account 入账批次生成: 科目 "
                + line.getAccountCode() + ", 指令类型 " + line.getInstructionType().getCode() + ", 行号 " + line.getLineNo());
        }
    }

    /** Owner Role 在指定指令类型目标账簿下解析出的 Owner 类型；BOOK_ENTITY 随账簿主体而定。 */
    static LedgerOwnerType ownerType(RuleOwnerRole role, AutoInstructionType instructionType) {
        return switch (role) {
            case BOOK_ENTITY -> instructionType == AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM
                ? LedgerOwnerType.FINANCE_ENTITY : LedgerOwnerType.OPERATING_ENTITY;
            case CHANNEL_ACCOUNT -> LedgerOwnerType.CHANNEL_ACCOUNT;
            case PLATFORM_FINANCE_ENTITY -> LedgerOwnerType.FINANCE_ENTITY;
            case TRANSACTION_OE -> LedgerOwnerType.OPERATING_ENTITY;
            case TAX_AUTHORITY -> LedgerOwnerType.TAX_AUTHORITY;
        };
    }

    /** 按稳定 Code 解析固定目录枚举，未知代码以业务错误拒绝。 */
    static <E extends Enum<E> & CodeEnum> E parse(Class<E> type, String field, String code) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(code)) {
                return constant;
            }
        }
        throw new BizCheckFailException("规则行" + field + "不在自动记账目录中: " + code);
    }
}
