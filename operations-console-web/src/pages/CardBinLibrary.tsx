import { useState } from 'react';
import { Button, Tabs } from 'antd';
import { ArrowRightOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import { useResource } from '../dataQuery/DataQueryParts';
import { hasButton } from '../dataQuery/dataQueryCommon';
import { formatShortDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { richText } from '../i18n/richText';
import { binLibraryManageButton, binLibraryPaths, csvTemplateFileName, csvTemplateHref, decodeActivationPage, formatCount } from '../card/binLibrary';
import type { BinLibraryActivation, BinLibraryVersion } from '../card/binLibrary';
import { ActivationModal, ImportModal } from '../card/BinLibraryModals';
import { MetaLine, OperatorName, useCountryFlags, useVersionCatalog } from '../card/BinLibraryParts';
import type { VersionCatalog } from '../card/BinLibraryParts';
import { BinRangesTab } from '../card/BinRangesTab';
import { BinVersionsTab } from '../card/BinVersionsTab';
import type { StatusFilter } from '../card/BinVersionsTab';
import '../dataQuery/dataQuery.css';
import '../card/binLibrary.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Step = { kind: 'import' } | { kind: 'activate'; version: BinLibraryVersion } | null;
type TabKey = 'ranges' | 'versions';

const activationsPath = `${binLibraryPaths.activations}?page=1&size=20`;

/**
 * 卡 BIN 库（画板 01—08）：页头（生效版本徽标 + 一行元信息 + CSV 模板 / 导入新版本）+ 号段数据 / 版本两个页签
 * <li>版本目录与激活记录在页面读取一次，页头、两个页签共用；导入、激活或回切后整体刷新。</li>
 * <li>没有维护按钮时页面只读：不显示导入、激活、回切与重新导入。</li>
 */
export function CardBinLibrary({ resources, onAuthorizationChanged }: Props) {
  const t = useT('card'), tNav = useT('nav');
  const canManage = hasButton(resources, binLibraryManageButton);
  const [revision, setRevision] = useState(0);
  const catalog = useVersionCatalog(revision);
  const activations = useResource(activationsPath, decodeActivationPage, t('history.loadFailed'));
  const flags = useCountryFlags(resources);
  const [tab, setTab] = useState<TabKey>('ranges');
  const [rangeVersionNo, setRangeVersionNo] = useState<number | null>(null);
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [selectedNo, setSelectedNo] = useState<number | null>(null);
  const [step, setStep] = useState<Step>(null);

  const reload = () => { setStep(null); setRevision(value => value + 1); activations.refresh(); };
  const showPending = () => { setStatus('VALID'); setSelectedNo(null); setTab('versions'); };
  const viewRanges = (versionNo: number) => { setRangeVersionNo(versionNo); setTab('ranges'); };
  const data = catalog.catalog;
  const active = data?.active ?? null;
  const rangeVersion = rangeVersionNo ?? active?.versionNo ?? null;
  const rangeCount = (data?.items.find(item => item.versionNo === rangeVersion) ?? (rangeVersion === active?.versionNo ? active : null))?.totalRows ?? 0;
  const history = activations.data?.items ?? [];

  return <div className="configuration-page dq-page bin-page">
    <header className="bin-header">
      <div className="bin-header-main">
        <div className="bin-title-row"><h1>{tNav('resource.OPERATIONS_CARD_BIN_LIBRARY')}</h1>
          {data && <span className={`bin-badge${active ? ' is-active' : ''}`}><i />{active ? t('page.activeBadge', { versionNo: active.versionNo }) : t('page.noActiveBadge')}</span>}</div>
        {data && <HeaderMeta catalog={data} history={history} onShowPending={showPending} />}
      </div>
      <div className="bin-header-actions">
        <Button icon={<DownloadOutlined />} href={csvTemplateHref} download={csvTemplateFileName}>{t('page.csvTemplate')}</Button>
        {canManage && <Button type="primary" icon={<UploadOutlined />} onClick={() => setStep({ kind: 'import' })}>{t('page.import')}</Button>}
      </div>
    </header>
    {catalog.failure && !data ? <QueryFailure failure={catalog.failure} retry={catalog.refresh} />
      : !data ? <Loading />
        : <Tabs className="bin-tabs" activeKey={tab} onChange={key => setTab(key as TabKey)} items={[
          { key: 'ranges', label: <span className="bin-tab">{t('tab.ranges')}<span className="bin-tab-count">{formatCount(rangeCount)}</span></span>,
            children: <BinRangesTab catalog={data} versionNo={rangeVersion} onVersionChange={setRangeVersionNo} flags={flags} canManage={canManage}
              onImport={() => setStep({ kind: 'import' })} onShowPending={showPending} /> },
          { key: 'versions', label: <span className="bin-tab">{t('tab.versions')}<span className="bin-tab-count">{formatCount(data.total)}</span>
            {data.pendingCount > 0 && <i className="bin-pending-dot" aria-label={t('page.pendingDot', { count: data.pendingCount })} />}</span>,
            children: <BinVersionsTab catalog={data} activations={history} canManage={canManage} status={status}
              onStatus={value => { setStatus(value); setSelectedNo(null); }} selectedNo={selectedNo ?? active?.versionNo ?? null} onSelect={setSelectedNo}
              onActivate={version => setStep({ kind: 'activate', version })} onImport={() => setStep({ kind: 'import' })} onViewRanges={viewRanges}
              onSettled={reload} /> },
        ]} />}
    {step?.kind === 'import' && <ImportModal onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={version => { setStatus('ALL'); setSelectedNo(version.versionNo); setTab('versions'); reload(); }} />}
    {step?.kind === 'activate' && <ActivationModal target={step.version} active={active} onClose={() => setStep(null)}
      onDone={() => { setSelectedNo(step.version.versionNo); reload(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 页头元信息：号段数 · 最近激活时间与操作人 · 待激活版本链接（切到版本页签并筛选可激活）。 */
function HeaderMeta({ catalog, history, onShowPending }: { catalog: VersionCatalog; history: BinLibraryActivation[]; onShowPending: () => void }) {
  const t = useT('card');
  const active = catalog.active;
  const activation = active ? history.find(item => item.versionNo === active.versionNo) ?? null : null;
  const pending = catalog.pendingCount > 0 && <button type="button" className="bin-link bin-pending-link" onClick={onShowPending}>
    <i className="bin-pending-dot" />{active ? t('page.pendingActivate', { count: catalog.pendingCount }) : t('page.pendingAvailable', { count: catalog.pendingCount })}
    <ArrowRightOutlined /></button>;
  if (!active) return <MetaLine className="bin-header-meta" parts={[t('page.noneActivated'), pending]} />;
  return <MetaLine className="bin-header-meta" parts={[t('page.rangeCount', { count: active.totalRows ?? 0 }),
    activation && <span>{richText(t, 'page.activatedBy', { operator: <OperatorName label={activation.operatorLabel} raw={activation.operatorAccountId} /> },
      { time: formatShortDateTime(activation.activatedAt) })}</span>, pending]} />;
}
