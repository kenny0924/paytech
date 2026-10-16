import { session } from '../api/session.ts';
import type { RequestClient } from '../api/session.ts';
import { invariant } from './riskDictionary.ts';
import { decodeEvaluation, decodeExplanation } from './riskEvaluation.ts';
import type { Evaluation, EvaluationExplanation } from './riskEvaluation.ts';

export const PAYMENT_ATTEMPT_POINT = 'PAYMENT_ATTEMPT_RISK';
export const CHECKOUT_PREPARE_POINT = 'CHECKOUT_PREPARE';
/** 按业务身份查询时，查询类型对应的风控点（支付尝试 ID → 支付尝试，订单 ID → 结账准备）。 */
export const QUERY_POINTS = { attempt: PAYMENT_ATTEMPT_POINT, order: CHECKOUT_PREPARE_POINT } as const;
const root = '/risk-decisions/v1/evaluations';
const enc = encodeURIComponent;

export interface RiskEvaluationApi {
  get(evaluationId: string, signal?: AbortSignal): Promise<Evaluation>;
  find(pointCode: string, businessKey: string, signal?: AbortSignal): Promise<Evaluation>;
  explanation(evaluationId: string, signal?: AbortSignal): Promise<EvaluationExplanation>;
}

/** 评估查询只读接口；回执的评估 ID、业务身份与请求不一致时按无效响应处理。响应含受限原值，不缓存、不记录。 */
export function createRiskEvaluationApi(client: RequestClient = session): RiskEvaluationApi {
  return {
    get: (evaluationId, signal) => client.request(`${root}/${enc(evaluationId)}`, { signal }, v => {
      const r = decodeEvaluation(v); invariant(r.evaluationId === evaluationId); return r;
    }),
    find: (pointCode, businessKey, signal) => client.request(`${root}?${new URLSearchParams({ pointCode, businessKey })}`, { signal }, v => {
      const r = decodeEvaluation(v); invariant(r.pointCode === pointCode && r.businessKey === businessKey && r.mode === 'PRIMARY'); return r;
    }),
    explanation: (evaluationId, signal) => client.request(`${root}/${enc(evaluationId)}/explanation`, { signal }, v => {
      const r = decodeExplanation(v); invariant(r.evaluationId === evaluationId); return r;
    }),
  };
}
export const riskEvaluationApi = createRiskEvaluationApi();
