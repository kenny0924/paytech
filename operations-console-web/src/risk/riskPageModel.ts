import type { ConsoleResource } from '../api/types.ts';
import { ApiError } from '../api/session.ts';
import { commandFailureSettlement, settlePendingCommand } from './riskCommands.ts';
import type { PendingCommand } from './riskCommands.ts';
import type { RuleSetEditorModel } from './ruleBuilder.ts';
import type { RiskDecisionApi } from './riskDecisionApi.ts';
import { translator } from '../i18n/index.ts';

const t = translator('risk');

export type RiskTab = 'rule-sets' | 'test-candidates';
export type RiskAction = 'SAVE' | 'FREEZE' | 'DELETE' | 'CREATE_CANDIDATE' | 'VALIDATE_CANDIDATE';
const actionResources: Readonly<Record<RiskAction, string>> = Object.freeze({
  DELETE: 'OPERATIONS_RISK_RULE_SET_DELETE',
  SAVE: 'OPERATIONS_RISK_RULE_SET_SAVE', FREEZE: 'OPERATIONS_RISK_RULE_SET_FREEZE',
  CREATE_CANDIDATE: 'OPERATIONS_RISK_TEST_CANDIDATE_CREATE', VALIDATE_CANDIDATE: 'OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE',
});
export class RiskActionUnavailableError extends Error {
  constructor() { super(t('command.actionUnavailable')); this.name = 'RiskActionUnavailableError'; }
}
export function parseRiskTab(search: string): RiskTab {
  const values = new URLSearchParams(search).getAll('tab');
  return values.length === 1 && values[0] === 'test-candidates' ? 'test-candidates' : 'rule-sets';
}
export function riskTabSearch(tab: RiskTab): string { return `?tab=${tab}`; }
export function hasRiskAction(resources: readonly ConsoleResource[], action: RiskAction): boolean {
  return resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === actionResources[action]);
}
export function requireRiskAction(resources: readonly ConsoleResource[], action: RiskAction): void {
  if (!hasRiskAction(resources, action)) throw new RiskActionUnavailableError();
}
/** 字段错误显示名是 `risk.fieldError.<字段>`。 */
const fieldNames = ['ruleSetCode', 'ruleSetName', 'changeReason', 'pointCode', 'ruleCode', 'ruleName', 'enabledFlag', 'reasonCode', 'reasonMessage'];
export function riskFieldErrorLabel(field: string): string {
  const fieldLabel = (name: string) => (fieldNames.includes(name) ? t('fieldError.' + name) : null);
  const direct = fieldLabel(field);
  if (direct) return direct;
  const rule = /^rules\[(\d{1,3})\](?:\.(.*))?$/.exec(field);
  if (!rule || Number(rule[1]) > 199) return t('fieldError.ruleConfiguration');
  const nested = rule[2] ?? '';
  const label = fieldLabel(nested) ?? (nested.startsWith('conditionNodes') ? t('fieldError.condition')
    : nested.startsWith('additionalActions') ? t('fieldError.additionalActions') : nested.startsWith('action') ? t('fieldError.terminalDecision') : t('fieldError.ruleConfiguration'));
  return t('fieldError.rule', { ruleNo: Number(rule[1]) + 1, label });
}

export type RiskConfirmation = Readonly<
  | { action: 'FREEZE'; ruleSetVersionId: string; payload: string }
  | { action: 'DELETE'; ruleSetId: string; ruleSetCode: string; ruleSetName: string; payload: string }
  | { action: 'CREATE_CANDIDATE'; payload: string }
  | { action: 'VALIDATE_CANDIDATE'; releaseId: string; payload: string }>;
export interface RiskPageCommandState {
  activeTab: RiskTab;
  editor: RuleSetEditorModel | null;
  confirmation: RiskConfirmation | null;
  selectedRuleSetVersionId: string | null;
  selectedCandidateReleaseId: string | null;
  pendingCommand: PendingCommand | null;
  fieldErrors: readonly { field: string; message: string }[];
}
export type RiskCommandResult =
  | { kind: 'SUCCESS'; action: 'SAVE'; ruleSetVersionId: string }
  | { kind: 'SUCCESS'; action: 'FREEZE'; ruleSetVersionId: string }
  | { kind: 'SUCCESS'; action: 'DELETE'; ruleSetId: string }
  | { kind: 'SUCCESS'; action: 'CREATE_CANDIDATE'; releaseId: string }
  | { kind: 'SUCCESS'; action: 'VALIDATE_CANDIDATE'; releaseId: string }
  | { kind: 'HTTP_FAILURE'; action: RiskAction; status: 400 | 401 | 403 | 404 | 409 | 413 | 422 | 429;
      code: string; message: string; requestId: string; fieldErrors: readonly { field: string; message: string }[] }
  | { kind: 'UNKNOWN_RESULT'; action: RiskAction; pendingCommand: PendingCommand; requestId: string };
const noFieldErrors: RiskPageCommandState['fieldErrors'] = Object.freeze([]);
export function classifyCommandFailure(action: RiskAction, pending: PendingCommand, error: unknown): RiskCommandResult {
  if (commandFailureSettlement(error) === 'DETERMINISTIC_HTTP_FAILURE' && error instanceof ApiError) {
    return Object.freeze({ kind: 'HTTP_FAILURE', action, status: error.status as Extract<RiskCommandResult, { kind: 'HTTP_FAILURE' }>['status'],
      code: error.code, message: error.message, requestId: error.requestId,
      fieldErrors: error.status === 422 && error.code === 'VALIDATION_ERROR' ? error.fieldErrors : noFieldErrors });
  }
  return Object.freeze({ kind: 'UNKNOWN_RESULT', action, pendingCommand: pending, requestId: error instanceof ApiError ? error.requestId : '' });
}
export function reduceRiskCommandResult(state: RiskPageCommandState, result: RiskCommandResult): RiskPageCommandState {
  const next = { ...state, fieldErrors: noFieldErrors };
  if (result.kind === 'UNKNOWN_RESULT') return { ...next, pendingCommand: settlePendingCommand(result.pendingCommand, 'UNKNOWN_RESULT') };
  next.pendingCommand = null;
  if (result.kind === 'HTTP_FAILURE') {
    if (result.status === 404) {
      if (result.action === 'VALIDATE_CANDIDATE') next.selectedCandidateReleaseId = null;
      else next.selectedRuleSetVersionId = null;
      next.confirmation = null;
    }
    if (result.status === 422) {
      next.confirmation = null;
      if (result.code === 'VALIDATION_ERROR') next.fieldErrors = result.fieldErrors;
    }
    return next;
  }
  next.confirmation = null;
  if (result.action === 'SAVE' || result.action === 'FREEZE') {
    next.selectedRuleSetVersionId = result.ruleSetVersionId;
    if (result.action === 'SAVE') next.editor = null;
  } else if (result.action === 'DELETE') {
    next.selectedRuleSetVersionId = null;
  } else {
    next.selectedCandidateReleaseId = result.releaseId;
    if (result.action === 'CREATE_CANDIDATE') next.activeTab = 'test-candidates';
  }
  return next;
}
export function commandFailureMessage(result: Exclude<RiskCommandResult, { kind: 'SUCCESS' }>): string {
  if (result.kind === 'UNKNOWN_RESULT') return result.action === 'DELETE'
    ? t('command.deleteUnknown')
    : t('command.resultUnknown');
  switch (result.status) {
    case 400: return result.message;
    case 401: return t('command.sessionExpired');
    case 403: return t('command.forbidden');
    case 404: return t('command.notFound');
    case 409:
      if (result.code === 'RISK_RULE_SET_IN_USE') return t('command.ruleSetInUse');
      if (result.code === 'RISK_RULE_SET_CHANGED') return t('command.ruleSetChanged');
      if (result.code === 'RISK_RULE_SET_DELETED') return t('command.ruleSetDeleted');
      return t('command.conflict');
    case 413: return result.action === 'SAVE' ? t('command.saveTooLarge') : t('command.commandTooLarge');
    case 422: return result.code === 'VALIDATION_ERROR' ? t('command.checkFields') : t('command.validationFailed');
    case 429: return t('command.tooManyRequests');
  }
}
export type RiskCommandTarget = Readonly<{ action: 'SAVE' } | { action: 'FREEZE'; ruleSetVersionId: string }
  | { action: 'DELETE'; ruleSetId: string }
  | { action: 'CREATE_CANDIDATE' } | { action: 'VALIDATE_CANDIDATE'; releaseId: string }>;
export function riskCommandPath(target: RiskCommandTarget): string {
  switch (target.action) {
    case 'SAVE': return '/decision-rule-sets/v1/rule-sets';
    case 'DELETE': return `/decision-rule-sets/v1/rule-sets/${encodeURIComponent(target.ruleSetId)}`;
    case 'FREEZE': return `/decision-rule-sets/v1/rule-sets/${encodeURIComponent(target.ruleSetVersionId)}/freeze`;
    case 'CREATE_CANDIDATE': return '/decision-releases/v1/candidates';
    case 'VALIDATE_CANDIDATE': return `/decision-releases/v1/candidates/${encodeURIComponent(target.releaseId)}/validate`;
  }
}
/** Re-read current permissions at the last synchronous boundary before each POST. BFF remains authoritative. */
export async function submitRiskCommand(api: RiskDecisionApi, target: RiskCommandTarget, pending: PendingCommand,
  resources: () => readonly ConsoleResource[], onAuthorizationChanged: () => void): Promise<RiskCommandResult> {
  if (pending.path !== riskCommandPath(target)) throw new Error(t('command.targetMismatch'));
  try {
    switch (target.action) {
      case 'SAVE': {
        requireRiskAction(resources(), 'SAVE');
        const result = await api.createRuleSetVersion(pending.payload, pending.key);
        return { kind: 'SUCCESS', action: 'SAVE', ruleSetVersionId: result.ruleSetVersionId };
      }
      case 'FREEZE': {
        requireRiskAction(resources(), 'FREEZE');
        const result = await api.freezeRuleSetVersion(target.ruleSetVersionId, pending.payload, pending.key);
        return { kind: 'SUCCESS', action: 'FREEZE', ruleSetVersionId: result.ruleSetVersionId };
      }
      case 'DELETE': {
        requireRiskAction(resources(), 'DELETE');
        const result = await api.deleteRuleSet(target.ruleSetId, pending.payload, pending.key);
        return { kind: 'SUCCESS', action: 'DELETE', ruleSetId: result.ruleSetId };
      }
      case 'CREATE_CANDIDATE': {
        requireRiskAction(resources(), 'CREATE_CANDIDATE');
        const result = await api.createCandidate(pending.payload, pending.key);
        return { kind: 'SUCCESS', action: 'CREATE_CANDIDATE', releaseId: result.releaseId };
      }
      case 'VALIDATE_CANDIDATE': {
        requireRiskAction(resources(), 'VALIDATE_CANDIDATE');
        const result = await api.validateCandidate(target.releaseId, pending.payload, pending.key);
        return { kind: 'SUCCESS', action: 'VALIDATE_CANDIDATE', releaseId: result.releaseId };
      }
    }
  } catch (error) {
    if (error instanceof RiskActionUnavailableError) { onAuthorizationChanged(); throw error; }
    if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
    return classifyCommandFailure(target.action, pending, error);
  }
}
