import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Select, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { RefreshButton } from '../components/RefreshButton';
import { useErrorNotification } from '../feedback/useErrorNotification';
import {
  BucketCheckModal, BucketModal, BucketUrlModal, CredentialModal, DeleteRouteModal, InstanceDrawer, RouteModal, StatusModal,
} from '../fileStorage/StorageModals';
import { ScanPolicyTab } from '../fileStorage/ScanPolicyTab';
import {
  accessLevelColors, accessLevelLabels, accessLevels, bucketObjectCode, bucketPath, bucketStatusBlocker, bucketRows, bucketStatusActions,
  bucketStatusLabels, checkResultLabels, credentialModeLabels, decodeInstanceList, decodeRouteList, defaultSourceSystem, deletableRoute,
  editablePublicBaseUrl, filterBucketRows, instancePath, instanceStatusAction, instanceStatusLabels, instanceStatusNotice, canManageFileStorage,
  missingRouteNotice, routeMatrix, statusColors, storageAdminPath, storageTypeLabels,
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
const date = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
const statusTag = (status: string, label: string) => <Tag color={statusColors[status]}>{label}</Tag>;
const levelTag = (level: AccessLevel) => <Tag color={accessLevelColors[level]}>{accessLevelLabels[level]}</Tag>;
const emptyPolicies: ScanPolicyList = { items: [], supportedMimeTypes: [] };

/** 实例与写入规则一起加载；任一失败则整页提示重试，不展示半套数据。 */
function useStorageData() {
  const notify = useErrorNotification();
  const [instances, setInstances] = useState<StorageInstance[]>([]); const [routes, setRoutes] = useState<StorageRoute[]>([]);
  const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    Promise.all([
      session.request(`${storageAdminPath}/instances`, { signal: controller.signal }, decodeInstanceList),
      session.request(`${storageAdminPath}/routes`, { signal: controller.signal }, decodeRouteList),
    ]).then(([nextInstances, nextRoutes]) => { if (!controller.signal.aborted) { setInstances(nextInstances); setRoutes(nextRoutes); } })
      .catch(error => { if (!controller.signal.aborted) { setInstances([]); setRoutes([]); setFailed(true); notify(error, '存储配置加载失败'); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [reload, notify]);
  return { instances, routes, loading, failed, refresh: () => setReload(value => value + 1) };
}

/** 检查策略单独加载：失败只影响策略页签与规则表单的策略下拉，不影响实例、桶与规则。 */
function useScanPolicies() {
  const notify = useErrorNotification();
  const [policies, setPolicies] = useState<ScanPolicyList>(emptyPolicies);
  const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    session.request(scanPolicyPath, { signal: controller.signal }, decodeScanPolicyList)
      .then(value => { if (!controller.signal.aborted) setPolicies(value); })
      .catch(error => { if (!controller.signal.aborted) { setPolicies(emptyPolicies); setFailed(true); notify(error, '检查策略加载失败'); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
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

  const instanceTable = <section className="table-panel">{toolbar(`共 ${data.instances.length} 个实例`,
    canManage && <Button type="primary" onClick={() => setDialog({ kind: 'instance', instance: null })}>＋ 新建实例</Button>)}
    <Table<StorageInstance> rowKey="instanceCode" tableLayout="fixed" dataSource={data.instances} loading={data.loading} pagination={false} scroll={{ x: 1400 }} columns={[
      { title: '代码', width: 150, render: (_, row) => <span className="mono">{row.instanceCode}</span> },
      { title: '类型', width: 100, render: (_, row) => storageTypeLabels[row.storageType] },
      { title: '地址', width: 220, ellipsis: true, render: (_, row) => row.endpoint ? <span className="mono">{row.endpoint}</span> : <span className="muted">区域默认地址</span> },
      { title: '区域', dataIndex: 'region', width: 120 },
      { title: '凭据方式', width: 130, render: (_, row) => credentialModeLabels[row.credentialMode] },
      { title: '凭据已配置', width: 160, render: (_, row) => row.credentialMode === 'IAM_ROLE' ? <span className="muted">不需要</span>
        : <div className="notification-refs">{row.credentialConfigured ? <span className="notification-ok">已配置</span> : <span className="notification-missing">未配置</span>}
          {row.credentialUpdateTime && <span className="muted">更换于 {date(row.credentialUpdateTime)}</span>}</div> },
      { title: '状态', width: 80, render: (_, row) => statusTag(row.status, instanceStatusLabels[row.status]) },
      { title: '桶数', width: 70, render: (_, row) => row.buckets.length },
      { title: '被规则引用', width: 100, render: (_, row) => row.routeCount },
      { title: '更新时间', width: 170, render: (_, row) => date(row.updateTime) },
      ...(canManage ? [{ title: '操作', width: 220, fixed: 'right' as const, render: (_: unknown, row: StorageInstance) => <Space size={4} wrap>
        <Button size="small" type="link" onClick={() => setDialog({ kind: 'instance', instance: row })}>编辑</Button>
        <Button size="small" type="link" onClick={() => setDialog({ kind: 'credential', instance: row })}>更换密钥</Button>
        <Button size="small" type="link" danger={instanceStatusAction(row).danger}
          onClick={() => setDialog({ kind: 'instance-status', instance: row })}>{instanceStatusAction(row).verb}</Button>
      </Space> }] : []),
    ]} /></section>;

  const buckets = filterBucketRows(bucketRows(data.instances, bucketFilter), levelFilter);
  const bucketTable = <section className="table-panel">{toolbar(`共 ${buckets.length} 个桶`,
    canManage && <Button type="primary" disabled={!data.instances.length} onClick={() => setDialog({ kind: 'bucket', instanceCode: bucketFilter })}>＋ 登记桶</Button>, <>
      <Select allowClear placeholder="全部实例" style={{ width: 180 }} value={bucketFilter} onChange={setBucketFilter}
        options={data.instances.map(instance => ({ value: instance.instanceCode, label: instance.instanceCode }))} />
      <Select allowClear placeholder="全部访问级别" style={{ width: 150 }} value={levelFilter} onChange={setLevelFilter}
        options={accessLevels.map(value => ({ value, label: accessLevelLabels[value] }))} /></>)}
    <Table<BucketRow> rowKey={row => `${row.instanceCode}/${row.bucketName}`} tableLayout="fixed" dataSource={buckets} loading={data.loading}
      pagination={false} scroll={{ x: 1320 }} columns={[
        { title: '桶', width: 220, render: (_, row) => <span className="mono">{row.bucketName}</span> },
        { title: '实例', width: 130, render: (_, row) => <span className="mono">{row.instanceCode}</span> },
        { title: '访问级别', width: 90, render: (_, row) => levelTag(row.accessLevel) },
        { title: '对外地址', width: 280, ellipsis: true, render: (_, row) => row.publicBaseUrl
          ? <span className="mono" title={row.publicBaseUrl}>{row.publicBaseUrl}</span> : <span className="muted">—</span> },
        { title: '状态', width: 80, render: (_, row) => statusTag(row.status, bucketStatusLabels[row.status]) },
        { title: '最近检查', width: 230, render: (_, row) => row.lastCheckResult ? <div className="notification-refs">
          <span className={row.lastCheckResult === 'OK' ? 'notification-ok' : 'notification-missing'}>
            {row.lastCheckResult === 'OK' ? '✓ ' : '✕ '}{checkResultLabels[row.lastCheckResult]}</span>
          <span className="muted">{date(row.lastCheckTime)}</span></div> : <span className="muted">未检查</span> },
        { title: '被规则引用', width: 100, render: (_, row) => row.routeCount },
        ...(canManage ? [{ title: '操作', width: 260, fixed: 'right' as const, render: (_: unknown, row: BucketRow) => <Space size={4} wrap>
          {row.status !== 'DISABLED' && <Button size="small" type="link" onClick={() => setDialog({ kind: 'check', bucket: row })}>检查</Button>}
          {editablePublicBaseUrl(row) && <Button size="small" type="link" onClick={() => setDialog({ kind: 'bucket-url', bucket: row })}>编辑对外地址</Button>}
          {bucketStatusActions(row).map(action => {
            const blocker = bucketStatusBlocker(row, action.target);
            return <Tooltip key={action.target} title={blocker}><Button size="small" type="link" danger={action.danger} disabled={!!blocker}
              onClick={() => setDialog({ kind: 'bucket-status', bucket: row, action })}>{action.verb}</Button></Tooltip>;
          })}
        </Space> }] : []),
      ]} />
    <div className="storage-level-notes">
      <div><strong>私有桶</strong><p>只通过平台签发的临时地址读写（最长 60 秒）。检查时要求不带凭据访问被拒绝；若桶允许匿名读取，检查不通过。</p></div>
      <div><strong>公开桶</strong><p>必须登记对外地址（CDN 或桶的公开地址）。检查时用对外地址匿名读取探测对象，读不到不通过。启用后对外地址不能再改。</p></div>
      <div><strong>写入规则</strong><p>上传声明公开还是私有；写入规则只能指向同一访问级别的桶，找不到同级别规则时直接报错，不会退回另一种桶。</p></div>
    </div></section>;

  const policyLabel = (code: string) => {
    const policy = scan.policies.items.find(item => item.policyCode === code);
    return policy?.activeRevisionNo ? `${code} r${policy.activeRevisionNo}` : code;
  };
  const routeCell = (row: RouteMatrixRow, level: AccessLevel) => {
    const route = row.routes[level];
    if (!route) return <div className="storage-route-cell is-missing"><span className="muted">{missingRouteNotice(data.routes, row.sourceSystem, level)}</span>
      {canManage && <Button size="small" type="link" onClick={() => setDialog({ kind: 'route', route: null, sourceSystem: row.sourceSystem, accessLevel: level })}>＋ 配置</Button>}</div>;
    return <div className="storage-route-cell">
      <div className="mono">{route.instanceCode} / {route.bucketName}</div>
      <div className="cell-meta"><span className="mono">{policyLabel(route.scanPolicyCode)}</span> · 上传期限 {route.maxUploadSeconds} 秒</div>
      {canManage && <Space size={4}>
        <Button size="small" type="link" onClick={() => setDialog({ kind: 'route', route })}>编辑</Button>
        {deletableRoute(route) && <Button size="small" type="link" danger onClick={() => setDialog({ kind: 'route-delete', route })}>删除</Button>}
      </Space>}</div>;
  };
  const matrix = routeMatrix(data.routes);
  const routeTable = <section className="table-panel">{toolbar(`共 ${data.routes.length} 条规则 · ${matrix.length} 个来源`,
    canManage && <Button type="primary" onClick={() => setDialog({ kind: 'route', route: null })}>＋ 新建规则</Button>)}
    <Table<RouteMatrixRow> rowKey="sourceSystem" tableLayout="fixed" dataSource={matrix} loading={data.loading} pagination={false} scroll={{ x: 1000 }} columns={[
      { title: '来源系统', width: 220, render: (_, row) => row.sourceSystem === defaultSourceSystem
        ? <div><Tag color="blue">*（默认）</Tag><div className="cell-meta">没有精确规则的来源使用</div></div> : <span className="mono">{row.sourceSystem}</span> },
      { title: <span>{levelTag('PRIVATE')}<span className="muted">上传未声明时默认</span></span>, render: (_, row) => routeCell(row, 'PRIVATE') },
      { title: <span>{levelTag('PUBLIC')}<span className="muted">上传声明 accessLevel=PUBLIC</span></span>, render: (_, row) => routeCell(row, 'PUBLIC') },
    ]} />
    <div className="notification-note">解析顺序：同来源同级别 → 默认 * 同级别 → 不可用（503）。不会把公开上传写进私有桶，也不会反过来。
      改规则只影响之后开始的上传；已有文件始终按记录上的实例和桶读取。</div></section>;

  return <div className="configuration-page notification-page">
    <div className="page-header"><h1>存储管理</h1></div>
    <div className="notification-note">登记存储实例与桶，按来源系统和访问级别决定新上传写到哪里；检查策略决定允许的文件类型和大小。实例代码与桶名创建后不可修改；
      桶由基础设施预先创建，Platform 只登记和检查，不建桶、不改桶策略。密钥保存在 Vault，录入后无法查看，只能更换。{!canManage && '当前账号只有查看权限。'}</div>
    {data.failed && <Alert type="warning" showIcon title="存储配置暂时无法加载" action={<Button onClick={data.refresh}>重试</Button>} />}
    <Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'instances', label: '存储实例', children: instanceTable },
      { key: 'buckets', label: '桶', children: bucketTable },
      { key: 'routes', label: '写入规则', children: routeTable },
      { key: 'policies', label: '检查策略', children: <>
        {scan.failed && <Alert type="warning" showIcon title="检查策略暂时无法加载" action={<Button onClick={scan.refresh}>重试</Button>} />}
        <ScanPolicyTab policies={scan.policies} loading={scan.loading} failed={scan.failed} refresh={refreshAll} canManage={canManage}
          onAuthorizationChanged={onAuthorizationChanged} /></> },
    ]} />
    {dialog?.kind === 'instance' && <InstanceDrawer instance={dialog.instance} {...commandProps} />}
    {dialog?.kind === 'credential' && <CredentialModal instance={dialog.instance} {...commandProps} />}
    {dialog?.kind === 'instance-status' && <StatusModal title={`${instanceStatusAction(dialog.instance).verb}存储实例 ${dialog.instance.instanceCode}`}
      path={`${instancePath(dialog.instance.instanceCode)}/status`} code={dialog.instance.instanceCode}
      target={instanceStatusAction(dialog.instance).target} version={dialog.instance.version} notice={instanceStatusNotice(dialog.instance)} {...commandProps} />}
    {dialog?.kind === 'bucket' && <BucketModal instances={data.instances} defaultInstance={dialog.instanceCode} {...commandProps} />}
    {dialog?.kind === 'bucket-url' && <BucketUrlModal bucket={dialog.bucket} {...commandProps} />}
    {dialog?.kind === 'check' && <BucketCheckModal bucket={dialog.bucket} onClose={() => { setDialog(null); data.refresh(); }}
      onEditUrl={canManage && editablePublicBaseUrl(dialog.bucket) ? () => setDialog({ kind: 'bucket-url', bucket: dialog.bucket }) : undefined}
      onDone={() => undefined} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'bucket-status' && <StatusModal title={`${dialog.action.verb}桶 ${dialog.bucket.bucketName}`}
      path={`${bucketPath(dialog.bucket.instanceCode, dialog.bucket.bucketName)}/status`}
      code={bucketObjectCode(dialog.bucket.instanceCode, dialog.bucket.bucketName)}
      target={dialog.action.target} version={dialog.bucket.version} notice={dialog.action.target === 'ACTIVE' ? '启用后可被写入规则引用并接受新上传。'
        : dialog.action.target === 'DISABLED' ? '停用后该桶上的文件读写都返回不可用，且不能再启用。' : '只读桶仍可下载，不接受新上传。'}
      {...commandProps} />}
    {dialog?.kind === 'route' && <RouteModal route={dialog.route} sourceSystem={dialog.sourceSystem} accessLevel={dialog.accessLevel}
      instances={data.instances} policies={scan.policies} {...commandProps} />}
    {dialog?.kind === 'route-delete' && <DeleteRouteModal route={dialog.route} {...commandProps} />}
  </div>;
}
