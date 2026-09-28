import { requestPage } from '../api/paging.ts';
import type { PagingRequestBody } from '../api/paging.ts';
import { session } from '../api/session.ts';
import type { RequestClient } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import type { DeleteRuleSetResponse, CreateRuleSetVersionResponse, CreateTestCandidateResponse, FreezeRuleSetVersionResponse, RuleAuthoringContext, RuleSetDetail, RuleSetListItem, TestCandidateDetail, TestCandidateListItem, ValidateTestCandidateResponse } from './riskApiModels.ts';
import { decodeDeleteRuleSetResponse, decodeCandidateDetail, decodeCandidatePage, decodeCreateCandidateResponse, decodeCreateRuleSetVersionResponse, decodeFreezeRuleSetVersionResponse, decodeRuleAuthoringContext, decodeRuleSetDetail, decodeRuleSetPage, decodeValidateCandidateResponse } from './riskResponseValidation.ts';

export type RuleSetFilters = Readonly<Partial<Record<'pointCode' | 'ruleSetCode' | 'status', string>>>;
export type CandidateFilters = Readonly<Partial<Record<'pointCode' | 'status', string>>>;
export interface RiskDecisionApi { deleteRuleSet(ruleSetId: string, payload: string, key: string): Promise<DeleteRuleSetResponse>; authoringContext(pointCode: string, signal?: AbortSignal): Promise<RuleAuthoringContext>; ruleSets(paging: PagingRequestBody, filters: RuleSetFilters, signal?: AbortSignal): Promise<PagingResponseBody<RuleSetListItem>>; ruleSet(ruleSetVersionId: string, signal?: AbortSignal): Promise<RuleSetDetail>; createRuleSetVersion(payload: string, key: string): Promise<CreateRuleSetVersionResponse>; freezeRuleSetVersion(ruleSetVersionId: string, payload: string, key: string): Promise<FreezeRuleSetVersionResponse>; candidates(paging: PagingRequestBody, filters: CandidateFilters, signal?: AbortSignal): Promise<PagingResponseBody<TestCandidateListItem>>; candidate(releaseId: string, signal?: AbortSignal): Promise<TestCandidateDetail>; createCandidate(payload: string, key: string): Promise<CreateTestCandidateResponse>; validateCandidate(releaseId: string, payload: string, key: string): Promise<ValidateTestCandidateResponse>; }
export interface RiskRequestClient extends RequestClient {}
const ruleSets = '/decision-rule-sets/v1/rule-sets'; const candidates = '/decision-releases/v1/candidates';
const encoded = (value: string) => encodeURIComponent(value);
const post = (payload: string, key: string): RequestInit => ({ method: 'POST', body: payload, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } });
export function createRiskDecisionApi(client: RiskRequestClient = session): RiskDecisionApi { return {
  deleteRuleSet: (ruleSetId, payload, key) => client.request(`${ruleSets}/${encoded(ruleSetId)}`, { ...post(payload, key), method: 'DELETE' }, value => {
    const response = decodeDeleteRuleSetResponse(value);
    const expectedVersion: unknown = JSON.parse(payload).expectedVersion;
    if (response.ruleSetId !== ruleSetId || typeof expectedVersion !== 'string'
      || BigInt(response.version) !== BigInt(expectedVersion) + 1n) throw new Error('invalid Risk response');
    return response;
  }),
  authoringContext: (pointCode, signal) => client.request(`/decision-rule-sets/v1/authoring-context/${encoded(pointCode)}`, { signal }, decodeRuleAuthoringContext),
  ruleSets: (paging, filters, signal) => requestPage(ruleSets, paging, signal, client, filters, decodeRuleSetPage),
  ruleSet: (ruleSetVersionId, signal) => client.request(`${ruleSets}/${encoded(ruleSetVersionId)}`, { signal }, decodeRuleSetDetail),
  createRuleSetVersion: (payload, key) => client.request(ruleSets, post(payload, key), decodeCreateRuleSetVersionResponse),
  freezeRuleSetVersion: (ruleSetVersionId, payload, key) => client.request(`${ruleSets}/${encoded(ruleSetVersionId)}/freeze`, post(payload, key), decodeFreezeRuleSetVersionResponse),
  candidates: (paging, filters, signal) => requestPage(candidates, paging, signal, client, filters, decodeCandidatePage),
  candidate: (releaseId, signal) => client.request(`${candidates}/${encoded(releaseId)}`, { signal }, decodeCandidateDetail),
  createCandidate: (payload, key) => client.request(candidates, post(payload, key), decodeCreateCandidateResponse),
  validateCandidate: (releaseId, payload, key) => client.request(`${candidates}/${encoded(releaseId)}/validate`, post(payload, key), decodeValidateCandidateResponse),
}; }
export const riskDecisionApi: RiskDecisionApi = createRiskDecisionApi();
