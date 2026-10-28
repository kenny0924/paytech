import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/session';
import { translator } from '../i18n/index';
import { checkPrerequisites, commercialErrorMessage } from './commercial';
import type { Agreement, PrerequisiteCheckResult } from './commercial';
import { freshPrerequisites, needsInitialCheck } from './capabilityGate';

const t = translator('commercial');

/**
 * 合同开通项前置核对（整份合同一次调用）：单飞；403 刷新授权；失败给出行内说明（commercial 透传原因优先）。
 * <li>核对可能迁移执行状态并推进合同版本：回执版本与页面不同时调用 onVersionAdvanced 刷新合同详情，避免后续写入 409。</li>
 * <li>fresh 只保留不早于当前合同版本的结果；stale 表示没有结果或之后发生过写入，需要重新核对；checkedAt 为本页最近一次核对完成的时间。</li>
 * <li>进入页面时若有等前置 / 前置失效暂停的成员开通项，自动核对一次，供「开通项生效」卡片与徽标小字给出卡住原因。</li>
 */
export function useCapabilityPrerequisites(agreement: Pick<Agreement, 'agreementId' | 'agreementType' | 'changeVersion' | 'capabilities'>,
  onAuthorizationChanged: () => void, onVersionAdvanced: () => void) {
  const [result, setResult] = useState<PrerequisiteCheckResult | null>(null);
  const [checkedAt, setCheckedAt] = useState<Date | null>(null);
  const [loading, setLoading] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const busy = useRef(false);
  const { agreementId, changeVersion } = agreement;
  const run = useCallback(async () => {
    if (busy.current) return;
    busy.current = true; setLoading(true); setFailure(null);
    try {
      const next = await checkPrerequisites(agreementId);
      setResult(next); setCheckedAt(new Date());
      if (next.changeVersion !== changeVersion) onVersionAdvanced();
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      setFailure(commercialErrorMessage(error, 'agreement') ?? t('prerequisite.failed'));
    } finally { busy.current = false; setLoading(false); }
  }, [agreementId, changeVersion, onAuthorizationChanged, onVersionAdvanced]);
  const initial = useRef<string | null>(null);
  const runRef = useRef(run);
  runRef.current = run;
  const autoCheck = needsInitialCheck(agreement);
  useEffect(() => {
    if (!autoCheck || initial.current === agreementId) return;
    initial.current = agreementId;
    void runRef.current();
  }, [agreementId, autoCheck]);
  return {
    fresh: freshPrerequisites(result, changeVersion), loading, failure, run, checkedAt,
    stale: !result || result.changeVersion < changeVersion,
  };
}

export type CapabilityPrerequisitesState = ReturnType<typeof useCapabilityPrerequisites>;
