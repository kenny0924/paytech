import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Modal, Tag } from 'antd';
import { requestAllPages } from '../api/paging';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';
import { activeStatusColors, activeStatusLabel, dataQueryPaths } from './dataQueryCommon';
import { decodeDataSourcePage } from './dataSourceModel';
import type { DataSource } from './dataSourceModel';
import type { DataQueryCommand } from './useDataQueryCommand';

/** 读取单个资源；path 为 null 时不请求（例如新建模板）。切换或卸载时取消旧请求。未给失败标题时用通用的"详情加载失败"。 */
export function useResource<T>(path: string | null, decode: (value: unknown) => T, notifyTitle?: string) {
  const notify = useErrorNotification(); const t = useT('data-query');
  const [data, setData] = useState<T>(); const [loading, setLoading] = useState(path !== null);
  const [failure, setFailure] = useState<unknown>(); const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (path === null) { setLoading(false); return; }
    const controller = new AbortController(); setLoading(true); setFailure(undefined);
    session.request<T>(path, { signal: controller.signal }, decode)
      .then(value => { if (!controller.signal.aborted) setData(value); })
      .catch(error => { if (!controller.signal.aborted) { setFailure(error); notify(error, notifyTitle ?? t('parts.detailLoadFailed')); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [path, decode, notify, notifyTitle, revision]);
  return { data, loading, failure, refresh: () => setRevision(v => v + 1) };
}

export function StatusTag({ status }: { status: string }) {
  return <Tag color={activeStatusColors[status] ?? 'default'}>{activeStatusLabel(status)}</Tag>;
}

/** 并发冲突：Owner 返回 409 时提示刷新，草稿保留。subject 为被修改的对象类型（默认"数据"）。 */
export function ConflictBanner({ onReload, subject = 'data' }: { onReload: () => void; subject?: 'data' | 'dataSource' | 'template' }) {
  const t = useT('data-query'), tCommon = useT('common');
  const title = subject === 'dataSource' ? t('parts.conflict.dataSource') : subject === 'template' ? t('parts.conflict.template') : t('parts.conflict.data');
  return <Alert type="error" showIcon className="dq-banner" title={title}
    action={<Button size="small" onClick={onReload}>{tCommon('action.refresh')}</Button>} />;
}

/** 弹窗外壳：保存中禁止关闭；409 横幅与业务错误统一放在内容上方。 */
export function CommandModal({ title, command, okText, onOk, onClose, onReload, children, width = 560, danger = false, okDisabled = false, footer }: {
  title: ReactNode; command: DataQueryCommand; okText?: string; onOk: () => void; onClose: () => void; onReload: () => void; children?: ReactNode;
  width?: number; danger?: boolean; okDisabled?: boolean; footer?: ReactNode;
}) {
  const tCommon = useT('common');
  return <Modal open wrapClassName="dq-modal" title={title} okText={okText ?? tCommon('action.save')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
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
  const notify = useErrorNotification(); const t = useT('data-query');
  const [items, setItems] = useState<DataSource[]>([]); const [loading, setLoading] = useState(true);
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages<DataSource>(dataQueryPaths.dataSources, controller.signal, session, {}, decodeDataSourcePage)
      .then(value => { if (!controller.signal.aborted) setItems(value); })
      .catch(error => { if (!controller.signal.aborted) notify(error, t('parts.dataSourcesLoadFailed')); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [notify]);
  return { items, loading };
}
