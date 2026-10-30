import { useState } from 'react';
import type { ReactNode } from 'react';
import { Button, Popover, Segmented } from 'antd';
import { useT } from '../i18n/react';
import type { JointCell, JointColumn } from './jointModel';
import type { RunNode } from './jointRun';

/** 详情一次先显示的行数；更多行“显示全部”在页面内展开。 */
export const collapsedRows = 10;

export interface NodeRows { rows: JointCell[][]; more: { loading: boolean; done: boolean; error: string | null } | null }

export interface DetailActions {
  /** 链路值 → 可定位的节点（不含自身）；没有时不显示“定位”。 */
  locate: (keyType: string, value: string, exceptId: string) => RunNode | null;
  onLocate: (node: RunNode) => void;
  /** 维度值可否另起查询（键类型可作入口且值合法）。 */
  canQuery: (keyType: string, value: string) => boolean;
  onQuery: (keyType: string, value: string) => void;
  onRetry: (node: RunNode) => void;
  onLoadMore: (node: RunNode) => void;
  retrying: boolean;
  failureReason: (node: RunNode) => string;
}

const cellText = (cell: JointCell) => cell === null ? null : String(cell);

/** 一个节点的明细：路径、表格（链路值可定位、维度值可“以此查询”）、显示全部 / 加载更多；失败与未查给出原因与重试。 */
export function NodeDetail({ node, color, path, rows, actions, header, extra, also = [] }: {
  node: RunNode; color: string; path: readonly RunNode[]; rows: NodeRows; actions: DetailActions; header?: ReactNode;
  /** 按对象合并后，这些对象也可经由的其他路径（模板名称连成的路径文字）。 */
  also?: readonly string[];
  /** 明细末尾的附加内容（未执行视图的按需查看）。 */
  extra?: ReactNode;
}) {
  const t = useT('data-query');
  const [technical, setTechnical] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const problem = node.status !== 'SUCCEEDED';
  const visible = expanded ? rows.rows : rows.rows.slice(0, collapsedRows);
  const hidden = rows.rows.length - visible.length;
  return <section className="jq-detail">
    <header className="jq-detail-head">
      <div className="jq-detail-title">
        <h3><span className="jq-dot" style={{ background: color }} />{node.templateName}
          {!problem && <span className="muted jq-detail-count">{t('joint.detail.rows', { count: rows.rows.length })}{node.truncated && !rows.more?.done ? '+' : ''}</span>}</h3>
        {path.length > 1 && <div className="muted jq-path">{t('joint.detail.path', { path: path.map(item => item.templateName).join(' → ') })}</div>}
        {also.map(item => <div key={item} className="muted jq-path jq-also">{t('joint.detail.alsoVia', { path: item })}</div>)}
      </div>
      <div className="jq-detail-tools">
        {header}
        {!problem && <button type="button" className="dq-link jq-tech-toggle" onClick={() => setTechnical(open => !open)} aria-expanded={technical}>{t('joint.detail.technical')}</button>}
      </div>
    </header>
    {technical && <TechnicalDetails node={node} />}
    {problem ? <div className="jq-detail-problem">
      <strong>{node.status === 'FAILED' ? t('joint.detail.failedTitle') : t('joint.detail.skippedTitle')}</strong>
      <p className="muted">{actions.failureReason(node)}</p>
      <div className="jq-detail-actions">
        <Button type="primary" loading={actions.retrying} onClick={() => actions.onRetry(node)}>{t('joint.detail.retry')}</Button>
        <Button onClick={() => setTechnical(open => !open)} aria-expanded={technical}>{t('joint.detail.technical')}</Button>
      </div>
    </div> : <>
      {node.warnings.some(warning => warning.startsWith('LINK_COLUMN_MISSING')) && <div className="dq-note is-warning">{t('joint.detail.linkColumnMissing')}</div>}
      {rows.rows.length === 0 ? <div className="muted jq-detail-empty">{t('joint.detail.noRows')}</div>
        : <div className="jq-table-wrap"><table className="jq-table">
          <thead><tr>{node.columns.map(column => <th key={column.name}>{column.label || column.name}</th>)}</tr></thead>
          <tbody>{visible.map((row, index) => <tr key={index}>{row.map((cell, column) =>
            <td key={column}><Cell cell={cell} column={node.columns[column]} node={node} actions={actions} /></td>)}</tr>)}</tbody>
        </table></div>}
      <div className="jq-detail-foot">
        {hidden > 0 && <button type="button" className="dq-link" onClick={() => setExpanded(true)}>{t('joint.detail.showAll', { count: hidden })}</button>}
        {hidden === 0 && node.truncated && !rows.more?.done && (node.pagingMode === 'NONE'
          ? <span className="muted">{t('joint.detail.truncatedNone', { count: node.rows.length })}</span>
          : <Button size="small" loading={rows.more?.loading} onClick={() => { setExpanded(true); actions.onLoadMore(node); }}>{t('joint.detail.loadMore')}</Button>)}
        {rows.more?.error && <span className="dq-text-error">{rows.more.error}</span>}
      </div>
    </>}
    {extra}
  </section>;
}

function Cell({ cell, column, node, actions }: { cell: JointCell; column: JointColumn | undefined; node: RunNode; actions: DetailActions }) {
  const t = useT('data-query');
  const text = cellText(cell);
  if (text === null) return <span className="muted">—</span>;
  if (!column?.keyType) return <span className={/^-?\d{9,}$/.test(text) ? 'mono' : undefined}>{text}</span>;
  const keyType = column.keyType;
  if (column.keyRole === 'LINK') {
    const target = actions.locate(keyType, text, node.id);
    return <span className="jq-link-cell"><span className="mono jq-link-value">{text}</span>
      {target && <button type="button" className="jq-locate" title={t('joint.detail.locateTitle', { name: target.templateName })}
        onClick={() => actions.onLocate(target)}>{t('joint.detail.locate')}</button>}</span>;
  }
  if (!actions.canQuery(keyType, text)) return <span className="mono">{text}</span>;
  return <Popover trigger={['hover', 'focus']} placement="top" content={<Button size="small" type="link" onClick={() => actions.onQuery(keyType, text)}>
    {t('joint.detail.queryThis')}</Button>}>
    <button type="button" className="mono jq-dimension" onClick={() => actions.onQuery(keyType, text)}>{text}</button>
  </Popover>;
}

/** 技术详情：模板编码、错误码、数据源、版本、触发键与跳数，只在展开时显示。 */
function TechnicalDetails({ node }: { node: RunNode }) {
  const t = useT('data-query');
  const items: [string, string][] = [
    [t('joint.tech.template'), `${node.templateCode}${node.versionNo ? ` · v${node.versionNo}` : ''}`],
    [t('joint.tech.dataSource'), node.dataSourceCode || '—'],
    [t('joint.tech.trigger'), `${node.triggerKeyType} · ${node.keyParameter} · ${t('joint.tech.values', { count: node.triggerValues.length })}`],
    [t('joint.tech.depth'), t('joint.tech.depthValue', { depth: node.depth })],
    [t('joint.tech.elapsed'), `${node.elapsedMillis} ms`],
    ...(node.errorCode ? [[t('joint.tech.errorCode'), node.errorCode] as [string, string]] : []),
    ...(node.errorMessage ? [[t('joint.tech.errorMessage'), node.errorMessage] as [string, string]] : []),
    ...(node.warnings.length ? [[t('joint.tech.warnings'), node.warnings.join(', ')] as [string, string]] : []),
  ];
  return <dl className="jq-tech">{items.map(([label, value]) => <div key={label}><dt>{label}</dt><dd className="mono">{value}</dd></div>)}</dl>;
}

export type DetailView = 'node' | 'all';
export function DetailSwitch({ view, total, onChange }: { view: DetailView; total: number; onChange: (view: DetailView) => void }) {
  const t = useT('data-query');
  return <Segmented size="small" value={view} onChange={value => onChange(value as DetailView)}
    options={[{ value: 'node', label: t('joint.detail.current') }, { value: 'all', label: t('joint.detail.all', { count: total }) }]} />;
}
