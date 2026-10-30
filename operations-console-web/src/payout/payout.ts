import { ApiError } from '../api/session.ts';
import type { ConsoleResource, PagingResponseBody } from '../api/types.ts';

/** 收款账户核验与 Owner 账户 / 余额（technique 8.5、10 节 O1—O5）的接口、路由、受控目录、解码与纯规则。 */
export const payoutApi = '/payout-admin/v1';

/** 页面路由（与 Platform 块 147 的 Console Resource frontend_route 一致）。 */
export const payoutRoutes = {
  reviews: '/finance/payout/beneficiary-reviews',
  owners: '/finance/payout/owners',
} as const;

/** 页面动作资源（块 147 按钮）：核验与原值挂核验页，代提交与停用 / 恢复 / 关闭挂 Owner 页。 */
export const payoutActions = {
  review: 'OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_REVIEW',
  reveal: 'OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_REVEAL',
  submit: 'OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_SUBMIT',
  manage: 'OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_MANAGE',
} as const;

export const ownerTypes = ['CLIENT', 'LOCAL_PARTNER'] as const;
export type OwnerType = typeof ownerTypes[number];
export const reviewStages = ['MAKER', 'CHECKER', 'DONE'] as const;
export type ReviewStage = typeof reviewStages[number];
export type AccountStatus = 'PENDING_REVIEW' | 'VERIFIED' | 'REJECTED' | 'SUSPENDED' | 'CLOSED';
export const railTypes = ['LOCAL', 'SWIFT'] as const;
/** 拒绝原因（BeneficiaryRejectReason）。 */
export const rejectReasons = ['HOLDER_NAME_MISMATCH', 'INVALID_ACCOUNT', 'UNSUPPORTED_BANK', 'DOCUMENT_REQUIRED', 'OTHER'] as const;
/** 运营可选的停用原因；PAYOUT_FAILURE 由 S2 / S3 出款失败自动使用，不在人工下拉。 */
export const suspendReasons = ['RISK_REVIEW', 'OWNER_REQUEST', 'OTHER'] as const;
/** 关闭原因（BeneficiaryCloseReason）。 */
export const closeReasons = ['OWNER_REQUEST', 'REPLACED', 'OPS'] as const;
/** 原值显示时长（秒），到期自动清除。 */
export const revealVisibleSeconds = 60;
/** 核验队列每页条数（payout 只允许 10、20、50）。 */
export const queuePageSizes = [10, 20, 50];
/** 核验备注与给 Client 的说明的最大长度（payout review_maker_note / reason_note）。 */
export const reviewNoteMaxLength = 500;

export interface AccountEvent {
  eventNo: number; fromStatus: string | null; toStatus: string; action: string; reasonCode: string | null;
  actorType: string; actorId: string | null; occurredTime: string;
}

export interface BeneficiaryAccount {
  beneficiaryAccountId: string; ownerType: OwnerType; ownerId: string; accountCode: string; railType: string; bankCountry: string;
  currency: string; bankName: string; bankCode: string | null; holderNameMasked: string; accountLast4: string; contentHash: string;
  status: AccountStatus; reasonCode: string | null; reasonNote: string | null; reviewMakerNote: string | null; submittedByType: string; submittedBy: string; reviewMakerId: string | null;
  reviewMakerDecision: 'APPROVE' | 'REJECT' | null; checkerId: string | null; checkerDecision: 'APPROVED' | 'REJECTED' | null;
  decisionTime: string | null; coolingUntil: string | null; usable: boolean; defaultAccount: boolean;
  sharedOwners: SharedOwner[] | null; version: string; createTime: string; updateTime: string; events: AccountEvent[] | null;
}

/** 以同一账号登记在其他 Owner 名下的未终结账户（只在运营视图，最多 5 条）。 */
export interface SharedOwner { ownerType: OwnerType; ownerId: string; accountCode: string; status: AccountStatus }
export interface PayoutOwner { ownerType: OwnerType; ownerId: string; ownerNo: string | null; legalName: string | null; status: string }
/** sharedOwnerProfiles 的键为 {ownerType}:{ownerId}；主档读取失败的 Owner 不在其中。 */
export interface ReviewItem { account: BeneficiaryAccount; owner: PayoutOwner | null; sharedOwnerProfiles: Record<string, PayoutOwner> }
export interface BeneficiaryDetail {
  account: BeneficiaryAccount; owner: PayoutOwner | null; sharedOwnerProfiles: Record<string, PayoutOwner>; operatorLabels: Record<string, string>;
}
export interface OwnerAccounts {
  ownerType: OwnerType; ownerId: string; owner: PayoutOwner | null; items: BeneficiaryAccount[]; operatorLabels: Record<string, string>;
}
export interface BalancePosition {
  entityNo: string; currency: string; pending: string; available: string; locked: string; securityDeposit: string | null;
  recoveryReceivable: string; blockReasons: string[];
}
export interface BalanceExplanation {
  ownerType: OwnerType; ownerId: string; positions: BalancePosition[];
  usableAccounts: { currency: string; count: number; defaultAccountId: string | null }[];
}
export interface FieldRequirement { code: string; required: boolean; oneOfGroup: string | null; pattern: string | null; maxLength: number; labelKey: string }
export interface Requirement { railType: string; bankCountry: string; currency: string; fields: FieldRequirement[] }
export interface RevealedValues {
  beneficiaryAccountId: string; holderName: string | null; iban: string | null; accountNumber: string | null;
  bankCode: string | null; swiftBic: string | null; accountType: string | null;
}

// ---------- 解码：缺少关键字段视为上游响应无效 ----------

const idPattern = /^[1-9][0-9]{0,18}$/;
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const text = (v: unknown) => typeof v === 'string' && v.length > 0;
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** 收款账户视图：标识、Owner、状态、摘要与版本必须存在；expected 给出时主键必须相等。 */
export function decodeAccount(value: unknown, expected?: string): BeneficiaryAccount {
  require(object(value) && typeof value.beneficiaryAccountId === 'string' && idPattern.test(value.beneficiaryAccountId)
    && (expected === undefined || value.beneficiaryAccountId === expected) && ownerTypes.includes(value.ownerType as OwnerType)
    && text(value.ownerId) && text(value.status) && text(value.contentHash) && text(value.version) && text(value.currency));
  return value as unknown as BeneficiaryAccount;
}

/** Owner 摘要可为空；存在时类型与标识必须存在。 */
function decodeOwner(value: unknown): PayoutOwner | null {
  if (value === null || value === undefined) return null;
  require(object(value) && ownerTypes.includes(value.ownerType as OwnerType) && text(value.ownerId));
  return value as unknown as PayoutOwner;
}

/** 共用 Owner 主档映射；缺失按空映射处理，存在的值按 Owner 摘要校验。 */
function decodeOwnerProfiles(value: unknown): Record<string, PayoutOwner> {
  if (value === null || value === undefined) return {};
  require(object(value));
  const profiles: Record<string, PayoutOwner> = {};
  Object.entries(value as Record<string, unknown>).forEach(([key, owner]) => {
    const decoded = decodeOwner(owner);
    if (decoded) profiles[key] = decoded;
  });
  return profiles;
}

/** 共用 Owner 的键（与 BFF 的 sharedOwnerProfiles 一致）。 */
export function sharedOwnerKey(owner: Pick<SharedOwner, 'ownerType' | 'ownerId'>) {
  return `${owner.ownerType}:${owner.ownerId}`;
}

/** 运营账号显示名映射；缺失按空映射处理。 */
function decodeLabels(value: unknown): Record<string, string> {
  if (value === null || value === undefined) return {};
  require(object(value) && Object.values(value).every(label => typeof label === 'string'));
  return value as Record<string, string>;
}

/** 核验队列分页。 */
export function decodeReviewPage(value: unknown): PagingResponseBody<ReviewItem> {
  require(object(value) && Array.isArray(value.items) && Number.isInteger(value.page) && Number.isInteger(value.size)
    && Number.isInteger(value.total));
  const page = value as unknown as PagingResponseBody<Record<string, unknown>>;
  return { ...page, items: page.items.map(item => {
    require(object(item));
    return { account: decodeAccount(item.account), owner: decodeOwner(item.owner), sharedOwnerProfiles: decodeOwnerProfiles(item.sharedOwnerProfiles) };
  }) };
}

/** 核验详情：主键必须等于请求，事件时间线不可缺失。 */
export function decodeDetail(expected: string) {
  return (value: unknown): BeneficiaryDetail => {
    require(object(value));
    const v = value as Record<string, unknown>;
    const account = decodeAccount(v.account, expected);
    require(Array.isArray(account.events));
    return { account, owner: decodeOwner(v.owner), sharedOwnerProfiles: decodeOwnerProfiles(v.sharedOwnerProfiles),
      operatorLabels: decodeLabels(v.operatorLabels) };
  };
}

/** Owner 账户列表：元素必须属于请求的 Owner。 */
export function decodeOwnerAccounts(ownerType: OwnerType, ownerId: string) {
  return (value: unknown): OwnerAccounts => {
    require(object(value) && value.ownerType === ownerType && value.ownerId === ownerId && Array.isArray(value.items));
    const v = value as Record<string, unknown>;
    const items = (v.items as unknown[]).map(item => decodeAccount(item));
    require(items.every(item => item.ownerType === ownerType && item.ownerId === ownerId));
    return { ownerType, ownerId, owner: decodeOwner(v.owner), items, operatorLabels: decodeLabels(v.operatorLabels) };
  };
}

/** 余额解释：必须属于请求的 Owner。 */
export function decodeBalance(ownerType: OwnerType, ownerId: string) {
  return (value: unknown): BalanceExplanation => {
    require(object(value) && value.ownerType === ownerType && value.ownerId === ownerId && Array.isArray(value.positions)
      && Array.isArray(value.usableAccounts));
    const v = value as Record<string, unknown>;
    (v.positions as unknown[]).forEach(position => require(object(position) && text(position.entityNo) && text(position.currency)
      && text(position.available) && Array.isArray(position.blockReasons)));
    return v as unknown as BalanceExplanation;
  };
}

/** 字段要求。 */
export function decodeRequirement(value: unknown): Requirement {
  require(object(value) && Array.isArray(value.fields)
    && (value.fields as unknown[]).every(field => object(field) && text(field.code)));
  return value as unknown as Requirement;
}

/** 原值：主键必须等于请求。 */
export function decodeReveal(expected: string) {
  return (value: unknown): RevealedValues => {
    require(object(value) && value.beneficiaryAccountId === expected);
    return value as unknown as RevealedValues;
  };
}

// ---------- 规则 ----------

export const hasAction = (resources: readonly ConsoleResource[], code: string) =>
  resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);

export const isAccountId = (value: string | null | undefined): value is string => !!value && idPattern.test(value);

/** 账户当前所在的核验阶段：待核验（无 Maker 意见）、待复核（有 Maker 意见）或已处理。 */
export function reviewStageOf(account: Pick<BeneficiaryAccount, 'status' | 'reviewMakerDecision'>): ReviewStage {
  if (account.status !== 'PENDING_REVIEW') return 'DONE';
  return account.reviewMakerDecision ? 'CHECKER' : 'MAKER';
}

/**
 * 四眼：当前账号是 Maker，或是运营代提交人时不能作 Checker 决定（payout 仍以 403 FOUR_EYES_REQUIRED 拒绝）
 * <li>返回阻断原因：MAKER 或 SUBMITTER；不阻断时为 null。</li>
 */
export function fourEyesBlock(account: Pick<BeneficiaryAccount, 'reviewMakerId' | 'submittedByType' | 'submittedBy'>,
  actorId: string): 'MAKER' | 'SUBMITTER' | null {
  if (account.reviewMakerId === actorId) return 'MAKER';
  if (account.submittedByType === 'OPERATIONS' && account.submittedBy === actorId) return 'SUBMITTER';
  return null;
}

/** Maker 建议拒绝时 Checker 只能驳回（payout 合同）。 */
export const checkerMayApprove = (account: Pick<BeneficiaryAccount, 'reviewMakerDecision'>) => account.reviewMakerDecision === 'APPROVE';

/** 名称比对的规范值：大写，去掉空白与标点。 */
export function normalizeName(value: string): string {
  return value.toUpperCase().replace(/[^\p{L}\p{N}]/gu, '');
}

export type NameMatch = 'EXACT' | 'NORMALIZED' | 'MISMATCH' | 'UNKNOWN';

/** 户名与法定名对照：完全一致、仅大小写 / 空格 / 标点不同、不一致；任一方缺失为 UNKNOWN。 */
export function compareNames(legalName: string | null | undefined, holderName: string | null | undefined): NameMatch {
  if (!legalName?.trim() || !holderName?.trim()) return 'UNKNOWN';
  if (legalName.trim() === holderName.trim()) return 'EXACT';
  return normalizeName(legalName) === normalizeName(holderName) ? 'NORMALIZED' : 'MISMATCH';
}

export type DisplayState = 'PENDING_REVIEW' | 'COOLING' | 'USABLE' | 'REJECTED' | 'SUSPENDED' | 'CLOSED';

/** 徽标状态：VERIFIED 按 usable 区分冷却中 / 可用（服务端实时判定）。 */
export function displayState(account: Pick<BeneficiaryAccount, 'status' | 'usable'>): DisplayState {
  if (account.status === 'VERIFIED') return account.usable ? 'USABLE' : 'COOLING';
  return account.status;
}

/** 可执行的状态命令：停用只对 VERIFIED，恢复只对 SUSPENDED，关闭对未终结账户。 */
export function availableCommands(account: Pick<BeneficiaryAccount, 'status'>): ('suspend' | 'resume' | 'close')[] {
  switch (account.status) {
    case 'VERIFIED': return ['suspend', 'close'];
    case 'SUSPENDED': return ['resume', 'close'];
    case 'PENDING_REVIEW': return ['close'];
    default: return [];
  }
}

export type LatestAction = { action: 'SUBMITTED' | 'MAKER_REVIEWED' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'CLOSED'; time: string; actorId: string | null };

/**
 * 列表“最近动作”：列表不含事件，按状态与已有字段推断
 * <li>已复核取决定时间与 Checker；待复核取更新时间与 Maker；停用 / 关闭取更新时间（操作人不在列表视图中）。</li>
 */
export function latestAction(account: BeneficiaryAccount): LatestAction {
  if ((account.status === 'VERIFIED' || account.status === 'REJECTED') && account.decisionTime) {
    return { action: account.status === 'VERIFIED' ? 'APPROVED' : 'REJECTED', time: account.decisionTime, actorId: account.checkerId };
  }
  if (account.status === 'SUSPENDED') return { action: 'SUSPENDED', time: account.updateTime, actorId: null };
  if (account.status === 'CLOSED') return { action: 'CLOSED', time: account.updateTime, actorId: null };
  if (account.status === 'REJECTED') return { action: 'REJECTED', time: account.updateTime, actorId: account.checkerId };
  if (account.reviewMakerDecision) return { action: 'MAKER_REVIEWED', time: account.updateTime, actorId: account.reviewMakerId };
  return { action: 'SUBMITTED', time: account.createTime, actorId: account.submittedByType === 'OPERATIONS' ? account.submittedBy : null };
}

/** 最近一条指定动作的事件。 */
export function lastEvent(account: Pick<BeneficiaryAccount, 'events'>, action: string): AccountEvent | undefined {
  return (account.events ?? []).filter(event => event.action === action).at(-1);
}

/** 余额分段条的比例：待结算 / 可用 / 锁定按金额占比（只用于宽度展示，金额文本仍用原字符串）。 */
export function balanceSegments(position: Pick<BalancePosition, 'pending' | 'available' | 'locked'>): { pending: number; available: number; locked: number } {
  const amounts = [position.pending, position.available, position.locked].map(value => Math.max(Number(value) || 0, 0));
  const total = amounts.reduce((sum, value) => sum + value, 0);
  if (total === 0) return { pending: 0, available: 0, locked: 0 };
  return { pending: amounts[0] / total, available: amounts[1] / total, locked: amounts[2] / total };
}

/** 金额大于零（十进制字符串）。 */
export const positive = (amount: string | null | undefined) => !!amount && /^[0-9]+(\.[0-9]+)?$/.test(amount) && /[1-9]/.test(amount);

/** 该币种冷却中账户最早的可用时间；没有冷却中账户时为 null。 */
export function earliestCoolingEnd(accounts: readonly BeneficiaryAccount[], currency: string): string | null {
  const ends = accounts.filter(account => account.currency === currency && displayState(account) === 'COOLING' && account.coolingUntil)
    .map(account => account.coolingUntil!).sort();
  return ends[0] ?? null;
}

/** Owner 查询串：类型与正整数标识都合法时返回，否则 null。 */
export function readOwnerQuery(search: string): { ownerType: OwnerType; ownerId: string } | null {
  const params = new URLSearchParams(search);
  const ownerType = params.get('ownerType') as OwnerType | null; const ownerId = params.get('ownerId');
  return ownerType && ownerTypes.includes(ownerType) && isAccountId(ownerId) ? { ownerType, ownerId } : null;
}

/** 队列 Query：阶段、可选 Owner、页码与条数。 */
export function reviewQuery(stage: ReviewStage, owner: { ownerType: OwnerType; ownerId: string } | null, page: number, size: number): string {
  const params = new URLSearchParams({ stage });
  if (owner) { params.set('ownerType', owner.ownerType); params.set('ownerId', owner.ownerId); }
  params.set('page', String(page)); params.set('size', String(size));
  return params.toString();
}

/** 代提交字段：按字段要求规范化（去空白；账号、IBAN、BIC 转大写）后校验必填、二选一、格式与长度，返回问题字段编码。 */
export function fieldIssues(requirement: Requirement, values: Readonly<Record<string, string | undefined>>): { code: string; issue: 'REQUIRED' | 'FORMAT' | 'ONE_OF' }[] {
  const issues: { code: string; issue: 'REQUIRED' | 'FORMAT' | 'ONE_OF' }[] = [];
  const value = (code: string) => normalizeField(code, values[code]);
  for (const field of requirement.fields) {
    const current = value(field.code);
    if (field.required && !current) issues.push({ code: field.code, issue: 'REQUIRED' });
    else if (current && field.pattern && !new RegExp(`^(?:${field.pattern})$`).test(current)) issues.push({ code: field.code, issue: 'FORMAT' });
  }
  const groups = new Map<string, string[]>();
  requirement.fields.filter(field => field.oneOfGroup).forEach(field => groups.set(field.oneOfGroup!, [...groups.get(field.oneOfGroup!) ?? [], field.code]));
  for (const codes of groups.values()) {
    if (codes.filter(code => value(code)).length !== 1) issues.push({ code: codes[0], issue: 'ONE_OF' });
  }
  return issues;
}

/** 字段规范化：户名与银行名去首尾空白；账号类字段去空白并转大写。 */
export function normalizeField(code: string, raw: string | undefined): string {
  if (!raw) return '';
  return ['iban', 'accountNumber', 'swiftBic', 'bankCode'].includes(code) ? raw.replace(/\s+/g, '').toUpperCase() : raw.trim();
}

/** 代提交请求的 fields：只带字段要求中出现且有值的字段。 */
export function submitFields(requirement: Requirement, values: Readonly<Record<string, string | undefined>>): Record<string, string> {
  return Object.fromEntries(requirement.fields.map(field => [field.code, normalizeField(field.code, values[field.code])])
    .filter(([, value]) => value));
}

// ---------- 页签计数 ----------

/** 显示计数的页签（已处理不显示）。 */
export const countedStages: readonly ReviewStage[] = ['MAKER', 'CHECKER'];
export type StageCounts = Partial<Record<ReviewStage, number>>;

/** 需要另发请求的计数页签：当前页签的计数取自已加载列表的 total，只查其他计数页签。 */
export function countStagesToFetch(active: ReviewStage): ReviewStage[] {
  return countedStages.filter(stage => stage !== active);
}

/**
 * 依次（一次一个）查询各页签计数：核验队列路由每人并发上限 2，列表已占一个，计数不得并发
 * <li>429 保留上一次计数；其他失败去掉该计数（页签不显示数字）；取消后立即停止，不再发请求。</li>
 */
export async function loadStageCounts(stages: readonly ReviewStage[], fetchTotal: (stage: ReviewStage, signal: AbortSignal) => Promise<number>,
  previous: StageCounts, signal: AbortSignal): Promise<StageCounts> {
  const next: StageCounts = { ...previous };
  for (const stage of stages) {
    if (signal.aborted) break;
    try {
      next[stage] = await fetchTotal(stage, signal);
    } catch (error) {
      if (signal.aborted) break;
      if (!(error instanceof ApiError && error.status === 429)) delete next[stage];
    }
  }
  return next;
}
