import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Select, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { RefreshButton } from '../components/RefreshButton';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  BucketCheckModal, BucketModal, BucketUrlModal, CredentialModal, DeleteRouteModal, InstanceDrawer, RouteModal, StatusModal,
} from '../fileStorage/StorageModals';
import { ScanPolicyTab } from '../fileStorage/ScanPolicyTab';
import {
  accessLevelColors, accessLevelLabel, accessLevels, bucketObjectCode, bucketPath, bucketStatusBlocker, bucketRows, bucketStatusActions,
  bucketStatusLabel, checkResultLabel, credentialModeLabel, decodeInstanceList, decodeRouteList, defaultSourceSystem, deletableRoute,
  editablePublicBaseUrl, filterBucketRows, instancePath, instanceStatusAction, instanceStatusLabel, instanceStatusNotice, canManageFileStorage,
  missingRouteNotice, routeMatrix, statusColors, storageAdminPath, storageTypeLabel,
} from '../fileStorage/fileStorage';
import type { AccessLevel, BucketRow, BucketStatus, RouteMatrixRow, StatusAction, StorageInstance, StorageRoute } from '../fileStorage/fileStorage';
import { decodeScanPolicyList, scanPolicyPath } from '../fileStorage/scanPolicy';
import type { ScanPolicyList } from '../fileStorage/scanPolicy';
import '../configuration/configuration.css';
import '../notification/notification.css';
import '../fileStorage/fileStorage.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog =
  | { kind: 'instance'; instance: StorageInstance | null } | { kind: 'credential' | 'instance-status'; instance: StorageInstance }
  | { kind: 'bucket'; instanceCode?: string } | { kind: 'check' | 'bucket-url'; bucket: BucketRow }
  | { kind: 'bucket-status'; bucket: BucketRow; action: StatusAction<BucketStatus> }
  | { kind: 'route'; route: StorageRoute | null; sourceSystem?: string; accessLevel?: AccessLevel } | { kind: 'route-delete'; route: StorageRoute };
const statusTag = (status: string, label: string) => <Tag color={statusColors[status]}>{label}</Tag>;
const levelTag = (level: AccessLevel) => <Tag color={accessLevelColors[level]}>{accessLevelLabel(level)}</Tag>;
const emptyPolicies: ScanPolicyList = { items: [], supportedMimeTypes: [] };

/** 实例与写入规则一起加载；任一失败则整页提示重试，不展示半套数据。 */
function useStorageData() {
  const notify = useErrorNotification(); const t = useT('file');
  const [instances, setInstances] = useState<StorageInstance[]>([]); const [routes, setRoutes] = useState<StorageRoute[]>([]);
  const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    Promise.all([
      session.request(`${storageAdminPath}/instances`, { signal: controller.signal }, decodeInstanceList),
      session.request(`${storageAdminPath}/routes`, { signal: controller.signal }, decodeRouteList),
    ]).then(([nextInstances, nextRoutes]) => { if (!controller.signal.aborted) { setInstances(nextInstances); setRoutes(nextRoutes); } })
      .catch(error => { if (!controller.signal.aborted) { setInstances([]); setRoutes([]); setFailed(true); notify(error, t('storage.loadFailed')); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [reload, notify]);
  return { instances, routes, loading, failed, refresh: () => setReload(value => value + 1) };
}

/** 检查策略单独加载：失败只影响策略页签与规则表单的策略下拉，不影响实例、桶与规则。 */
function useScanPolicies() {
  const notify = useErrorNotification(); const t = useT('file');
  const [policies, setPolicies] = useState<ScanPolicyList>(emptyPolicies);
  const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    session.request(scanPolicyPath, { signal: controller.signal }, decodeScanPolicyList)
      .then(value => { if (!controller.signal.aborted) setPolicies(value); })
      .catch(error => { if (!controller.signal.aborted) { setPolicies(emptyPolicies); setFailed(true); notify(error, t('policy.loadFailed')); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [reload, notify]);
  return { policies, loading, failed, refresh: () => setReload(value => value + 1) };
}

/**
 * 存储管理：存储实例、桶、写入规则与检查策略四个页签
 * <li>有维护按钮时可新建、编辑、更换密钥、启停、登记与检查桶、维护写入规则与检查策略；否则只读。</li>
 * <li>桶与写入规则区分私有 / 公开；写入规则按「来源系统 × 访问级别」矩阵展示。</li>
 * <li>实例代码与桶名创建后不可修改；桶由基础设施预先创建，这里只登记和检查。</li>
 */
export function FileStorage({ resources, onAuthorizationChanged }: Props) {
  const canManage = canManageFileStorage(resources);
  const t = useT('file'), tCommon = useT('common'), tNav = useT('nav');
  const data = useStorageData(); const scan = useScanPolicies();
  const [tab, setTab] = useState('instances'); const [bucketFilter, setBucketFilter] = useState<string>();
  const [levelFilter, setLevelFilter] = useState<AccessLevel>();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const refreshAll = () => { data.refresh(); scan.refresh(); };
  const done = () => { setDialog(null); refreshAll(); };
  const commandProps = { onClose: () => setDialog(null), onDone: done, onAuthorizationChanged };
  const toolbar = (count: string, action?: ReactNode, filters?: ReactNode) => <div className="table-toolbar">
    <Space wrap>{filters}<span className="table-count"><span>{count}</span>
      <RefreshButton onClick={refreshAll} loading={data.loading} failed={data.failed} /></span></Space>
    <Space>{action}</Space></div>;

  const instanceTable = <section className="table-panel">{toolbar(t('instances.count', { count: data.instances.length }),
    canManage && <Button type="primary" onClick={() => setDialog({ kind: 'instance', instance: null })}>{t('instances.create')}</Button>)}
    <Table<StorageInstance> rowKey="instanceCode" tableLayout="fixed" dataSource={data.instances} loading={data.loading} pagination={false} scroll={{ x: 1400 }} columns={[
      { title: t('instances.column.code'), width: 150, render: (_, row) => <span className="mono">{row.instanceCode}</span> },
      { title: t('instances.column.type'), width: 100, render: (_, row) => storageTypeLabel(row.storageType) },
      { title: t('instances.column.endpoint'), width: 220, ellipsis: true, render: (_, row) => row.endpoint ? <span className="mono">{row.endpoint}</span> : <span className="muted">{t('instances.defaultEndpoint')}</span> },
      { title: t('instances.column.region'), dataIndex: 'region', width: 120 },
      { title: t('instances.column.credentialMode'), width: 130, render: (_, row) => credentialModeLabel(row.credentialMode) },
      { title: t('instances.column.credentialConfigured'), width: 160, render: (_, row) => row.credentialMode === 'IAM_ROLE' ? <span className="muted">{t('instances.credentialNotNeeded')}</span>
        : <div className="notification-refs">{row.credentialConfigured ? <span className="notification-ok">{t('instances.credentialConfigured')}</span> : <span className="notification-missing">{t('instances.credentialNotConfigured')}</span>}
          {row.credentialUpdateTime && <span className="muted">{t('instances.credentialReplacedAt', { time: formatDateTime(row.credentialUpdateTime) })}</span>}</div> },
      { title: t('instances.column.status'), width: 80, render: (_, row) => statusTag(row.status, instanceStatusLabel(row.status)) },
      { title: t('instances.column.bucketCount'), width: 90, render: (_, row) => row.buckets.length },
      { title: t('instances.column.routeCount'), width: 100, render: (_, row) => row.routeCount },
      { title: t('instances.column.updatedAt'), width: 170, render: (_, row) => formatDateTime(row.updateTime) },
      ...(canManage ? [{ title: t('instances.column.actions'), width: 220, fixed: 'right' as const, render: (_: unknown, row: StorageInstance) => <Space size={4} wrap>
        <Button size="small" type="link" onClick={() => setDialog({ kind: 'instance', instance: row })}>{t('instances.edit')}</Button>
        <Button size="small" type="link" onClick={() => setDialog({ kind: 'credential', instance: row })}>{t('instances.replaceCredential')}</Button>
        <Button size="small" type="link" danger={instanceStatusAction(row).danger}
          onClick={() => setDialog({ kind: 'instance-status', instance: row })}>{instanceStatusAction(row).verb}</Button>
      </Space> }] : []),
    ]} /></section>;

  const buckets = filterBucketRows(bucketRows(data.instances, bucketFilter), levelFilter);
  const bucketTable = <section className="table-panel">{toolbar(t('buckets.count', { count: buckets.length }),
    canManage && <Button type="primary" disabled={!data.instances.length} onClick={() => setDialog({ kind: 'bucket', instanceCode: bucketFilter })}>{t('buckets.register')}</Button>, <>
      <Select allowClear placeholder={t('buckets.allInstances')} style={{ width: 180 }} value={bucketFilter} onChange={setBucketFilter}
        options={data.instances.map(instance => ({ value: instance.instanceCode, label: instance.instanceCode }))} />
      <Select allowClear placeholder={t('buckets.allAccessLevels')} style={{ width: 150 }} value={levelFilter} onChange={setLevelFilter}
        options={accessLevels.map(value => ({ value, label: accessLevelLabel(value) }))} /></>)}
    <Table<BucketRow> rowKey={row => `${row.instanceCode}/${row.bucketName}`} tableLayout="fixed" dataSource={buckets} loading={data.loading}
      pagination={false} scroll={{ x: 1320 }} columns={[
        { title: t('buckets.column.bucket'), width: 220, render: (_, row) => <span className="mono">{row.bucketName}</span> },
        { title: t('buckets.column.instance'), width: 130, render: (_, row) => <span className="mono">{row.instanceCode}</span> },
        { title: t('bucket.accessLevel'), width: 90, render: (_, row) => levelTag(row.accessLevel) },
        { title: t('bucket.publicBaseUrl'), width: 280, ellipsis: true, render: (_, row) => row.publicBaseUrl
          ? <span className="mono" title={row.publicBaseUrl}>{row.publicBaseUrl}</span> : <span className="muted">—</span> },
        { title: t('instances.column.status'), width: 80, render: (_, row) => statusTag(row.status, bucketStatusLabel(row.status)) },
        { title: t('buckets.column.lastCheck'), width: 230, render: (_, row) => row.lastCheckResult ? <div className="notification-refs">
          <span className={row.lastCheckResult === 'OK' ? 'notification-ok' : 'notification-missing'}>
            {row.lastCheckResult === 'OK' ? '✓ ' : '✕ '}{checkResultLabel(row.lastCheckResult)}</span>
          <span className="muted">{formatDateTime(row.lastCheckTime)}</span></div> : <span className="muted">{t('buckets.notChecked')}</span> },
        { title: t('instances.column.routeCount'), width: 100, render: (_, row) => row.routeCount },
        ...(canManage ? [{ title: t('instances.column.actions'), width: 260, fixed: 'right' as const, render: (_: unknown, row: BucketRow) => <Space size={4} wrap>
          {row.status !== 'DISABLED' && <Button size="small" type="link" onClick={() => setDialog({ kind: 'check', bucket: row })}>{t('buckets.check')}</Button>}
          {editablePublicBaseUrl(row) && <Button size="small" type="link" onClick={() => setDialog({ kind: 'bucket-url', bucket: row })}>{t('bucket.editUrl')}</Button>}
          {bucketStatusActions(row).map(action => {
            const blocker = bucketStatusBlocker(row, action.target);
            return <Tooltip key={action.target} title={blocker}><Button size="small" type="link" danger={action.danger} disabled={!!blocker}
              onClick={() => setDialog({ kind: 'bucket-status', bucket: row, action })}>{action.verb}</Button></Tooltip>;
          })}
        </Space> }] : []),
      ]} />
    <div className="storage-level-notes">
      <div><strong>{t('buckets.notes.privateTitle')}</strong><p>{t('buckets.notes.private')}</p></div>
      <div><strong>{t('buckets.notes.publicTitle')}</strong><p>{t('buckets.notes.public')}</p></div>
      <div><strong>{t('buckets.notes.routeTitle')}</strong><p>{t('buckets.notes.route')}</p></div>
    </div></section>;

  const policyLabel = (code: string) => {
    const policy = scan.policies.items.find(item => item.policyCode === code);
    return policy?.activeRevisionNo ? `${code} r${policy.activeRevisionNo}` : code;
  };
  const routeCell = (row: RouteMatrixRow, level: AccessLevel) => {
    const route = row.routes[level];
    if (!route) return <div className="storage-route-cell is-missing"><span className="muted">{missingRouteNotice(data.routes, row.sourceSystem, level)}</span>
      {canManage && <Button size="small" type="link" onClick={() => setDialog({ kind: 'route', route: null, sourceSystem: row.sourceSystem, accessLevel: level })}>{t('routes.configure')}</Button>}</div>;
    return <div className="storage-route-cell">
      <div className="mono">{route.instanceCode} / {route.bucketName}</div>
      <div className="cell-meta"><span className="mono">{policyLabel(route.scanPolicyCode)}</span> · {t('routes.uploadSeconds', { seconds: route.maxUploadSeconds })}</div>
      {canManage && <Space size={4}>
        <Button size="small" type="link" onClick={() => setDialog({ kind: 'route', route })}>{t('instances.edit')}</Button>
        {deletableRoute(route) && <Button size="small" type="link" danger onClick={() => setDialog({ kind: 'route-delete', route })}>{t('routes.delete')}</Button>}
      </Space>}</div>;
  };
  const matrix = routeMatrix(data.routes);
  const routeTable = <section className="table-panel">{toolbar(t('routes.count', { count: data.routes.length, sources: matrix.length }),
    canManage && <Button type="primary" onClick={() => setDialog({ kind: 'route', route: null })}>{t('routes.create')}</Button>)}
    <Table<RouteMatrixRow> rowKey="sourceSystem" tableLayout="fixed" dataSource={matrix} loading={data.loading} pagination={false} scroll={{ x: 1000 }} columns={[
      { title: t('route.sourceSystem'), width: 220, render: (_, row) => row.sourceSystem === defaultSourceSystem
        ? <div><Tag color="blue">{t('routes.defaultTag')}</Tag><div className="cell-meta">{t('routes.defaultHint')}</div></div> : <span className="mono">{row.sourceSystem}</span> },
      { title: <span>{levelTag('PRIVATE')}<span className="muted">{t('routes.privateColumnHint')}</span></span>, render: (_, row) => routeCell(row, 'PRIVATE') },
      { title: <span>{levelTag('PUBLIC')}<span className="muted">{t('routes.publicColumnHint')}</span></span>, render: (_, row) => routeCell(row, 'PUBLIC') },
    ]} />
    <div className="notification-note">{t('routes.note')}</div></section>;

  return <div className="configuration-page notification-page">
    <div className="page-header"><h1>{tNav('resource.OPERATIONS_FILE_STORAGE')}</h1></div>
    <div className="notification-note">{canManage ? t('storage.intro') : t('storage.introReadOnly')}</div>
    {data.failed && <Alert type="warning" showIcon title={t('storage.unavailable')} action={<Button onClick={data.refresh}>{tCommon('action.retry')}</Button>} />}
    <Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'instances', label: t('storage.tab.instances'), children: instanceTable },
      { key: 'buckets', label: t('storage.tab.buckets'), children: bucketTable },
      { key: 'routes', label: t('storage.tab.routes'), children: routeTable },
      { key: 'policies', label: t('storage.tab.policies'), children: <>
        {scan.failed && <Alert type="warning" showIcon title={t('policy.unavailable')} action={<Button onClick={scan.refresh}>{tCommon('action.retry')}</Button>} />}
        <ScanPolicyTab policies={scan.policies} loading={scan.loading} failed={scan.failed} refresh={refreshAll} canManage={canManage}
          onAuthorizationChanged={onAuthorizationChanged} /></> },
    ]} />
    {dialog?.kind === 'instance' && <InstanceDrawer instance={dialog.instance} {...commandProps} />}
    {dialog?.kind === 'credential' && <CredentialModal instance={dialog.instance} {...commandProps} />}
    {dialog?.kind === 'instance-status' && <StatusModal title={instanceStatusAction(dialog.instance).target === 'ACTIVE'
      ? t('instances.enableTitle', { code: dialog.instance.instanceCode }) : t('instances.disableTitle', { code: dialog.instance.instanceCode })}
      failureTitle={instanceStatusAction(dialog.instance).target === 'ACTIVE'
        ? t('instances.enableFailed', { code: dialog.instance.instanceCode }) : t('instances.disableFailed', { code: dialog.instance.instanceCode })}
      path={`${instancePath(dialog.instance.instanceCode)}/status`} code={dialog.instance.instanceCode}
      target={instanceStatusAction(dialog.instance).target} version={dialog.instance.version} notice={instanceStatusNotice(dialog.instance)} {...commandProps} />}
    {dialog?.kind === 'bucket' && <BucketModal instances={data.instances} defaultInstance={dialog.instanceCode} {...commandProps} />}
    {dialog?.kind === 'bucket-url' && <BucketUrlModal bucket={dialog.bucket} {...commandProps} />}
    {dialog?.kind === 'check' && <BucketCheckModal bucket={dialog.bucket} onClose={() => { setDialog(null); data.refresh(); }}
      onEditUrl={canManage && editablePublicBaseUrl(dialog.bucket) ? () => setDialog({ kind: 'bucket-url', bucket: dialog.bucket }) : undefined}
      onDone={() => undefined} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'bucket-status' && <StatusModal title={dialog.action.target === 'ACTIVE' ? t('buckets.enableTitle', { name: dialog.bucket.bucketName })
      : dialog.action.target === 'READ_ONLY' ? t('buckets.readOnlyTitle', { name: dialog.bucket.bucketName }) : t('buckets.disableTitle', { name: dialog.bucket.bucketName })}
      failureTitle={dialog.action.target === 'ACTIVE' ? t('buckets.enableFailed', { name: dialog.bucket.bucketName })
        : dialog.action.target === 'READ_ONLY' ? t('buckets.readOnlyFailed', { name: dialog.bucket.bucketName }) : t('buckets.disableFailed', { name: dialog.bucket.bucketName })}
      path={`${bucketPath(dialog.bucket.instanceCode, dialog.bucket.bucketName)}/status`}
      code={bucketObjectCode(dialog.bucket.instanceCode, dialog.bucket.bucketName)}
      target={dialog.action.target} version={dialog.bucket.version} notice={dialog.action.target === 'ACTIVE' ? t('buckets.enableNotice')
        : dialog.action.target === 'DISABLED' ? t('buckets.disableNotice') : t('buckets.readOnlyNotice')}
      {...commandProps} />}
    {dialog?.kind === 'route' && <RouteModal route={dialog.route} sourceSystem={dialog.sourceSystem} accessLevel={dialog.accessLevel}
      instances={data.instances} policies={scan.policies} {...commandProps} />}
    {dialog?.kind === 'route-delete' && <DeleteRouteModal route={dialog.route} {...commandProps} />}
  </div>;
}
