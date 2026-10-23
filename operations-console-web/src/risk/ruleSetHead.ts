import type { RiskDecisionApi } from './riskDecisionApi.ts';
import type { RuleSetDetail, RuleSetVersionSummary } from './riskApiModels.ts';
import { translator } from '../i18n/index.ts';

export class ObservedHeadChangedError extends Error {
  constructor() {
    super(translator('risk')('ruleSet.headChanged'));
    this.name = 'ObservedHeadChangedError';
  }
}

function sameVersion(left: RuleSetVersionSummary, right: RuleSetVersionSummary): boolean {
  return left.ruleSetVersionId === right.ruleSetVersionId && left.versionNo === right.versionNo
    && left.pointVersionId === right.pointVersionId && left.pointCode === right.pointCode
    && left.ruleSetCode === right.ruleSetCode && left.contentHash === right.contentHash;
}

/** 只观察 Owner 返回的当前配置；保存、冻结的最终并发判断仍由 Risk 执行。 */
export async function loadObservedRuleSetHead(api: RiskDecisionApi, pointCode: string,
  ruleSetCode: string, signal: AbortSignal): Promise<RuleSetDetail | null> {
  signal.throwIfAborted();
  const result = await api.ruleSets({ page: 1, size: 10 }, { pointCode, ruleSetCode }, signal);
  signal.throwIfAborted();
  if (result.page !== 1 || result.size !== 10 || (result.total !== 0 && result.total !== 1)
    || result.items.length !== result.total) throw new ObservedHeadChangedError();
  const head = result.items[0] ?? null;
  if (head && (head.pointCode !== pointCode || head.ruleSetCode !== ruleSetCode || head.status === 'RETIRED')) {
    throw new ObservedHeadChangedError();
  }
  if (head === null) return null;
  signal.throwIfAborted();
  const detail = await api.ruleSet(head.ruleSetVersionId, signal);
  signal.throwIfAborted();
  if (!sameVersion(head, detail)) throw new ObservedHeadChangedError();
  return detail;
}

export function requireSelectedObservedHead(selected: RuleSetDetail, observed: RuleSetDetail | null): RuleSetDetail {
  if (observed === null || !sameVersion(selected, observed)) throw new ObservedHeadChangedError();
  return observed;
}

export function requireUnusedRuleSetCode(observed: RuleSetDetail | null): void {
  if (observed !== null) throw new ObservedHeadChangedError();
}
