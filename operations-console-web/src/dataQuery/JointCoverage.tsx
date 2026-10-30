import { useState } from 'react';
import { Drawer } from 'antd';
import { ApiError } from '../api/session';
import { useT } from '../i18n/react';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import { dataQueryPaths } from './dataQueryCommon';
import { decodeJointCoverage } from './jointModel';
import type { EntryKeyType } from './jointModel';
import { useResource } from './DataQueryParts';

/**
 * 覆盖范围（原型 08）：按当前启用的模板推算从所选单号能查到的数据（J2），按分组列出业务名称；
 * 断点列在“暂时查不到”。模板编码与原因码只在“技术详情”中。
 */
export function JointCoverage({ keyType, colors, keyTypeName, onClose }: {
  keyType: EntryKeyType; colors: ReadonlyMap<string, string>; keyTypeName: (code: string) => string | null; onClose: () => void;
}) {
  const t = useT('data-query');
  // 失败只在抽屉内显示一次（400 显示 Problem detail，其余为通用失败面板），不再弹通知。
  const coverage = useResource(dataQueryPaths.jointGraph(keyType.keyTypeCode), decodeJointCoverage, t('joint.coverage.loadFailed'), true);
  const [technical, setTechnical] = useState(false);
  const data = coverage.data;
  return <Drawer open rootClassName="dq-modal jq-coverage" size={480} title={t('joint.coverage.title', { name: keyType.keyTypeName })} onClose={onClose} destroyOnHidden>
    <p className="muted">{t('joint.coverage.intro')}</p>
    {coverage.loading && !data ? <Loading /> : !data ? <CoverageFailure failure={coverage.failure} retry={coverage.refresh} /> : <>
      {data.groups.length === 0 ? <p className="muted">{t('joint.coverage.none')}</p>
        : <dl className="jq-coverage-list">{data.groups.map(group => <div key={group.groupName}>
          <dt><span className="jq-dot" style={{ background: colors.get(group.groupName) ?? '#a0aec0' }} />{group.groupName}</dt>
          <dd>{[...new Set(group.templates.map(item => item.templateName))].join(t('joint.listSeparator'))}</dd>
        </div>)}</dl>}
      {data.breaks.length > 0 && <section className="jq-coverage-breaks">
        <h4>{t('joint.coverage.unreachable')}</h4>
        {data.breaks.map(item => <p key={item.keyType} className="muted">{keyTypeName(item.keyType)
          ? t('joint.coverage.break', { name: keyTypeName(item.keyType)! }) : item.reason}</p>)}
        <p className="muted">{t('joint.coverage.breakHint')}</p>
      </section>}
      {data.excluded.length > 0 && <section className="jq-coverage-breaks">
        <button type="button" className="dq-link" onClick={() => setTechnical(open => !open)} aria-expanded={technical}>
          {t('joint.coverage.excluded', { count: data.excluded.length })}</button>
        {technical && <dl className="jq-tech">{data.excluded.map(item => <div key={item.templateCode}><dt className="mono">{item.templateCode}</dt>
          <dd>{item.reason}</dd></div>)}</dl>}
      </section>}
    </>}
  </Drawer>;
}

/** 入口类型无效时 Platform 返回 400（Problem detail）：直接显示原因（兼容 422 字段错误）；其余按通用失败处理。 */
function CoverageFailure({ failure, retry }: { failure: unknown; retry: () => void }) {
  if (failure instanceof ApiError && (failure.status === 400 || failure.status === 422)) {
    const message = failure.fieldErrors.map(item => item.message).join(' ') || failure.detail || failure.message;
    return <div className="dq-note is-error">{message}</div>;
  }
  return <QueryFailure failure={failure} retry={retry} />;
}
