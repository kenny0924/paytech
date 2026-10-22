import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Modal, Tag } from 'antd';
import { requestAllPages } from '../api/paging';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { activeStatusColors, activeStatusLabels, dataQueryPaths } from './dataQueryCommon';
import { decodeDataSourcePage } from './dataSourceModel';
import type { DataSource } from './dataSourceModel';
import type { DataQueryCommand } from './useDataQueryCommand';

/** 读取单个资源；path 为 null 时不请求（例如新建模板）。切换或卸载时取消旧请求。 */
export function useResource<T>(path: string | null, decode: (value: unknown) => T, notifyTitle = '详情加载失败') {
  const notify = useErrorNotification();
  const [data, setData] = useState<T>(); const [loading, setLoading] = useState(path !== null);
  const [failure, setFailure] = useState<unknown>(); const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (path === null) { setLoading(false); return; }
    const controller = new AbortController(); setLoading(true); setFailure(undefined);
    session.request<T>(path, { signal: controller.signal }, decode)
      .then(value => { if (!controller.signal.aborted) setData(value); })
      .catch(error => { if (!controller.signal.aborted) { setFailure(error); notify(error, notifyTitle); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [path, decode, notify, notifyTitle, revision]);
  return { data, loading, failure, refresh: () => setRevision(v => v + 1) };
}

export function StatusTag({ status }: { status: string }) {
  return <Tag color={activeStatusColors[status] ?? 'default'}>{activeStatusLabels[status] ?? status}</Tag>;
}

/** 并发冲突：Owner 返回 409 时提示刷新，草稿保留。 */
export function ConflictBanner({ onReload, subject = '数据' }: { onReload: () => void; subject?: string }) {
  return <Alert type="error" showIcon className="dq-banner" title={`${subject}已被其他操作修改（版本冲突），请刷新后再试。`}
    action={<Button size="small" onClick={onReload}>刷新</Button>} />;
}

/** 弹窗外壳：保存中禁止关闭；409 横幅与业务错误统一放在内容上方。 */
export function CommandModal({ title, command, okText = '保存', onOk, onClose, onReload, children, width = 560, danger = false, okDisabled = false, footer }: {
  title: ReactNode; command: DataQueryCommand; okText?: string; onOk: () => void; onClose: () => void; onReload: () => void; children?: ReactNode;
  width?: number; danger?: boolean; okDisabled?: boolean; footer?: ReactNode;
}) {
  return <Modal open wrapClassName="dq-modal" title={title} okText={okText} cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict || okDisabled, danger }} onOk={onOk} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden width={width} {...(footer !== undefined ? { footer } : {})}>
    {command.conflict && <ConflictBanner onReload={onReload} />}
    {command.formError && <Alert type="error" showIcon className="dq-banner" title={command.formError} />}
    {children}
  </Modal>;
}

export function Note({ children, tone }: { children: ReactNode; tone?: 'warning' | 'error' }) {
  return <div className={`dq-note${tone ? ` is-${tone}` : ''}`}>{children}</div>;
}

/** 全部未删除的数据源（每页 200 分批读取），供筛选与模板编辑器选择。失败时为空列表并提示。 */
export function useDataSourceOptions() {
  const notify = useErrorNotification();
  const [items, setItems] = useState<DataSource[]>([]); const [loading, setLoading] = useState(true);
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages<DataSource>(dataQueryPaths.dataSources, controller.signal, session, {}, decodeDataSourcePage)
      .then(value => { if (!controller.signal.aborted) setItems(value); })
      .catch(error => { if (!controller.signal.aborted) notify(error, '数据源列表加载失败'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [notify]);
  return { items, loading };
}
