import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Tag, Tooltip } from 'antd';
import { session } from '../api/session';
import { requestPage } from '../api/paging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { countryName, decodeCountries, referenceDataPaths, referenceDataTabs } from '../referenceData/referenceData';
import type { ConsoleResource } from '../api/types';
import { binLibraryPaths, brandMarks, commonIssuerCountries, decodeVersionPage, operatorDisplay, versionStatusColors } from './binLibrary';
import type { BinLibraryVersion, BinRange, VersionStatus } from './binLibrary';

export function StatusTag({ status }: { status: VersionStatus }) {
  const t = useT('card');
  return <Tag className="bin-status" color={versionStatusColors[status]}>{enumLabel(t, 'enum.versionStatus', status)}</Tag>;
}

/** 卡组织：品牌色块缩写 + 名称（名称可省略，用于卡片与匹配条）。 */
export function BrandBadge({ brand, withName = true }: { brand: string; withName?: boolean }) {
  const t = useT('card');
  const mark = brandMarks[brand];
  return <span className="bin-brand">
    <span className="bin-brand-mark" style={mark ? { background: mark.color } : undefined}>{mark?.mark ?? brand}</span>
    {withName && <span>{enumLabel(t, 'enum.cardBrand', brand)}</span>}
  </span>;
}

/** 发卡国：国旗（有 Platform 国旗地址时）+ 代码 + 当前语言名称。 */
export function CountryCell({ code, flagUrl, compact = false }: { code: string; flagUrl: string | null; compact?: boolean }) {
  const name = countryName(code, null);
  return <span className="bin-country">
    {flagUrl && <img className="bin-flag" src={flagUrl} alt="" loading="lazy" />}
    <strong>{code}</strong>{!compact && name !== code && <span className="bin-country-name">{name}</span>}
  </span>;
}

/** 操作人：显示名、非数字原值或截断 ID；截断时悬停看完整 ID。 */
export function OperatorName({ label, raw }: { label: string | null; raw: string | null }) {
  const display = operatorDisplay(label, raw);
  return display.full ? <Tooltip title={<span className="mono">{display.full}</span>}><span className="bin-operator">{display.text}</span></Tooltip>
    : <span className="bin-operator">{display.text}</span>;
}

/**
 * 一行元信息：各片段之间用圆点分隔，空片段不占位
 * <li>圆点挂在片段左侧外边距里，内层整体左移一个间隔、外层裁切：换行后位于行首的圆点被裁掉，圆点不会出现在行首或行尾。</li>
 */
export function MetaLine({ parts, className }: { parts: ReactNode[]; className?: string }) {
  const shown = parts.filter(part => part !== null && part !== false && part !== undefined && part !== '');
  return <div className={`bin-meta${className ? ` ${className}` : ''}`}><div className="bin-meta-inner">
    {shown.map((part, index) => <span key={index} className="bin-meta-part"><span className="bin-meta-text">{part}</span></span>)}
  </div></div>;
}

export function BinPrefix({ range }: { range: Pick<BinRange, 'binPrefix' | 'prefixLength'> }) {
  const t = useT('card');
  return <span className="bin-prefix"><span className="mono-strong">{range.binPrefix}</span>
    {range.prefixLength === 8 && <span className="bin-length-tag">{t('ranges.eightDigits')}</span>}</span>;
}

export const dash = (value: ReactNode) => value ? value : <span className="bin-dash">—</span>;

export function EmptyState({ icon, title, description, actions }: { icon: ReactNode; title: string; description?: ReactNode; actions?: ReactNode }) {
  return <div className="bin-empty"><span className="bin-empty-icon">{icon}</span><strong>{title}</strong>
    {description && <p>{description}</p>}{actions && <div className="bin-empty-actions">{actions}</div>}</div>;
}

export interface VersionCatalog {
  /** 最近的版本（版本号倒序，至多 100 个）。 */
  items: BinLibraryVersion[];
  total: number;
  /** 全部版本都在 items 里：此时状态计数准确。 */
  complete: boolean;
  active: BinLibraryVersion | null;
  pendingCount: number;
}

const catalogSize = 100;

/**
 * 版本目录：一次读取最近 100 个版本，页头、版本选择、版本列表与状态计数共用，不再按状态各发一次
 * <li>版本列表路由按登录账号限并发 2：超过 100 个版本时才另外逐个补查生效版本与可激活数，不并发发出（阶段 5 缺陷 S5-01）。</li>
 */
export function useVersionCatalog(revision: number) {
  const [catalog, setCatalog] = useState<VersionCatalog | null>(null);
  const [loading, setLoading] = useState(true); const [failure, setFailure] = useState<unknown>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); const { signal } = controller;
    setLoading(true); setFailure(undefined);
    void (async () => {
      try {
        const page = await requestPage(binLibraryPaths.versions, { page: 1, size: catalogSize }, signal, session, {}, decodeVersionPage);
        const complete = page.total <= page.items.length;
        let active = page.items.find(item => item.status === 'ACTIVE') ?? null;
        let pendingCount = page.items.filter(item => item.status === 'VALID').length;
        if (!complete) {
          if (!active) active = (await requestPage(binLibraryPaths.versions, { page: 1, size: 10 }, signal, session, { status: 'ACTIVE' }, decodeVersionPage)).items[0] ?? null;
          pendingCount = (await requestPage(binLibraryPaths.versions, { page: 1, size: 10 }, signal, session, { status: 'VALID' }, decodeVersionPage)).total;
        }
        if (!signal.aborted) setCatalog({ items: page.items, total: page.total, complete, active, pendingCount });
      } catch (error) {
        if (!signal.aborted) setFailure(error);
      } finally {
        if (!signal.aborted) setLoading(false);
      }
    })();
    return () => controller.abort();
  }, [revision, retry]);
  return { catalog, loading, failure, refresh: () => setRetry(value => value + 1) };
}

/**
 * 国旗与发卡国筛选清单：有"参考数据"菜单时读取一次 Platform 国家（ACTIVE），否则只显示代码与名称、筛选用常用国家
 * <li>国旗只是装饰：读取失败不提示，不影响号段列表。</li>
 */
export function useCountryFlags(resources: readonly ConsoleResource[]) {
  const allowed = referenceDataTabs(resources).includes('countries');
  const [flags, setFlags] = useState<ReadonlyMap<string, string>>(new Map());
  const [codes, setCodes] = useState<readonly string[]>(commonIssuerCountries);
  useEffect(() => {
    if (!allowed) return;
    const controller = new AbortController();
    session.request(referenceDataPaths.countries, { signal: controller.signal }, decodeCountries).then(countries => {
      if (controller.signal.aborted) return;
      const active = countries.filter(country => country.status === 'ACTIVE');
      setFlags(new Map(active.flatMap(country => country.logoPublicUrl ? [[country.countryCode, country.logoPublicUrl] as const] : [])));
      if (active.length) setCodes(active.map(country => country.countryCode).sort());
    }).catch(() => undefined);
    return () => controller.abort();
  }, [allowed]);
  return { flagOf: (code: string) => flags.get(code) ?? null, countryCodes: codes };
}
export type CountryFlags = ReturnType<typeof useCountryFlags>;
