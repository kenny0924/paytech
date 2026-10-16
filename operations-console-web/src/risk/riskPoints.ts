import { useEffect, useState } from 'react';
import { createVariableApi } from './riskVariable.ts';

export interface RiskPoint { pointCode: string; pointName: string }
/** 已知风控点的中文名；目录读取失败时也作为下拉兜底。 */
export const KNOWN_POINTS: readonly RiskPoint[] = [{ pointCode: 'CHECKOUT_PREPARE', pointName: '结账准备' }, { pointCode: 'PAYMENT_ATTEMPT_RISK', pointName: '支付尝试' }];
/** 有认证要求（3DS）输出的风控点；执行发布与评估查询据此显示认证相关指标。 */
export const AUTHENTICATION_OUTPUT_POINTS: readonly string[] = ['PAYMENT_ATTEMPT_RISK'];
export function hasAuthenticationOutput(pointCode: string): boolean { return AUTHENTICATION_OUTPUT_POINTS.includes(pointCode); }
export function pointName(points: readonly RiskPoint[], pointCode: string): string { return points.find(p => p.pointCode === pointCode)?.pointName ?? pointCode; }

/**
 * 可选风控点以 Risk 变量创作上下文的 supportedPoints 为准（它列出全部已登记风控点；决策流上下文只返回所请求的一个点）。
 * 显示名优先用已知中文名，未知风控点用 Risk 返回的名称；目录为空时回退已知列表。
 */
export function mergeRiskPoints(catalog: readonly RiskPoint[]): readonly RiskPoint[] {
  if (!catalog.length) return KNOWN_POINTS;
  return catalog.map(p => ({ pointCode: p.pointCode, pointName: KNOWN_POINTS.find(k => k.pointCode === p.pointCode)?.pointName ?? p.pointName }));
}

/** 规则集、决策流、执行发布等页面共用的风控点下拉选项。 */
export function useRiskPoints(): readonly RiskPoint[] {
  const [points, setPoints] = useState<readonly RiskPoint[]>(KNOWN_POINTS);
  useEffect(() => {
    const abort = new AbortController();
    createVariableApi().context(abort.signal)
      .then(context => { if (!abort.signal.aborted) setPoints(mergeRiskPoints(context.supportedPoints)); })
      .catch(() => undefined);
    return () => abort.abort();
  }, []);
  return points;
}
