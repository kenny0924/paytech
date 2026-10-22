import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { Result } from 'antd';
import { requestAllPages } from '../api/paging';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import { channelPaths, channelRoutes, decodeChannelPage } from './channel';
import { legacyIntegrationTarget, parseLegacyIntegrationPath } from './channelWorkspace';

type Resolution = { state: 'loading' } | { state: 'found'; channelId: string } | { state: 'missing' } | { state: 'failed'; failure: unknown };

/** 按编码查渠道 ID：关键字查询后取编码完全相同的一条（编码全局唯一）。 */
function useChannelIdByCode(channelCode: string | null): [Resolution, () => void] {
  const [resolution, setResolution] = useState<Resolution>({ state: 'loading' });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!channelCode) return undefined;
    const controller = new AbortController();
    setResolution({ state: 'loading' });
    requestAllPages(channelPaths.channels, controller.signal, undefined, { keyword: channelCode }, decodeChannelPage)
      .then(items => {
        const match = items.find(item => item.channelCode === channelCode);
        setResolution(match ? { state: 'found', channelId: match.channelId } : { state: 'missing' });
      })
      .catch(failure => { if (!controller.signal.aborted) setResolution({ state: 'failed', failure }); });
    return () => controller.abort();
  }, [channelCode, attempt]);
  return [resolution, () => setAttempt(value => value + 1)];
}

function ChannelMissing({ channelCode }: { channelCode: string }) {
  return <Result status="404" title="渠道不存在" subTitle={`没有编码为 ${channelCode} 的渠道。原接入方已并入渠道，请在渠道列表中确认编码。`}
    extra={<Link to={channelRoutes.channels}>返回渠道列表</Link>} />;
}

/** /channel/channels/code/:channelCode[子路径]：解析后 replace 到渠道详情的同一页面。 */
export function ChannelCodeRedirect({ channelCode, suffix }: { channelCode: string; suffix: string }) {
  const location = useLocation();
  const [resolution, retry] = useChannelIdByCode(channelCode);
  if (resolution.state === 'loading') return <Loading />;
  if (resolution.state === 'failed') return <QueryFailure failure={resolution.failure} retry={retry} />;
  if (resolution.state === 'missing') return <ChannelMissing channelCode={channelCode} />;
  return <Navigate replace to={`${channelRoutes.channel(resolution.channelId)}${suffix}${location.search}`} />;
}

/**
 * 旧接入方地址 /external-api/integrations/:code/...：按编码查渠道 ID 后 replace 到渠道详情的同名页签；
 * 列表与新建地址回到渠道列表；查不到时显示「渠道不存在」。
 */
export function LegacyIntegrationRedirect() {
  const location = useLocation();
  const legacy = parseLegacyIntegrationPath(location.pathname);
  const [resolution, retry] = useChannelIdByCode(legacy?.code ?? null);
  if (!legacy) return <Result status="404" title="页面不存在" />;
  if (legacy.code === null) return <Navigate replace to={channelRoutes.channels} />;
  if (resolution.state === 'loading') return <Loading />;
  if (resolution.state === 'failed') return <QueryFailure failure={resolution.failure} retry={retry} />;
  if (resolution.state === 'missing') return <ChannelMissing channelCode={legacy.code} />;
  const target = legacyIntegrationTarget(resolution.channelId, legacy.rest, location.search);
  return target ? <Navigate replace to={target} /> : <Navigate replace to={channelRoutes.channel(resolution.channelId)} />;
}
