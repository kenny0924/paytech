import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Select, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import {
  BucketCheckModal, BucketModal, CredentialModal, DeleteRouteModal, InstanceDrawer, RouteModal, StatusModal,
} from '../fileStorage/StorageModals';
import {
  bucketObjectCode, bucketPath, bucketStatusBlocker, bucketRows, bucketStatusActions, bucketStatusLabels, checkResultLabels, credentialModeLabels,
  decodeInstanceList, decodeRouteList, deletableRoute, instancePath, instanceStatusAction, instanceStatusLabels, instanceStatusNotice,
  canManageFileStorage, sourceSystemLabel, statusColors, storageAdminPath, storageTypeLabels,
} from '../fileStorage/fileStorage';
import type { BucketRow, BucketStatus, StatusAction, StorageInstance, StorageRoute } from '../fileStorage/fileStorage';
import '../configuration/configuration.css';
import '../notification/notification.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog =
  | { kind: 'instance'; instance: StorageInstance | null } | { kind: 'credential' | 'instance-status'; instance: StorageInstance }
  | { kind: 'bucket'; instanceCode?: string } | { kind: 'check'; bucket: BucketRow }
  | { kind: 'bucket-status'; bucket: BucketRow; action: StatusAction<BucketStatus> }
  | { kind: 'route'; route: StorageRoute | null } | { kind: 'route-delete'; route: StorageRoute };
const date = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
const statusTag = (status: string, label: string) => <Tag color={statusColors[status]}>{label}</Tag>;

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

/**
 * 存储管理：存储实例、桶与写入规则三个页签
 * <li>有维护按钮时可新建、编辑、更换密钥、启停、登记与检查桶、维护写入规则；否则只读。</li>
 * <li>实例代码与桶名创建后不可修改；桶由基础设施预先创建，这里只登记和检查。</li>
 */
export function FileStorage({ resources, onAuthorizationChanged }: Props) {
  const canManage = canManageFileStorage(resources);
  const data = useStorageData();
  const [tab, setTab] = useState('instances'); const [bucketFilter, setBucketFilter] = useState<string>();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const done = () => { setDialog(null); data.refresh(); };
  const commandProps = { onClose: () => setDialog(null), onDone: done, onAuthorizationChanged };
  const toolbar = (count: string, action?: ReactNode) => <div className="table-toolbar"><span>{count}</span>
    <Space>{action}<Button loading={data.loading} onClick={data.refresh}>刷新</Button></Space></div>;

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

  const buckets = bucketRows(data.instances, bucketFilter);
  const bucketTable = <section className="table-panel">{toolbar(`共 ${buckets.length} 个桶`, <>
    <Select allowClear placeholder="全部实例" style={{ width: 200 }} value={bucketFilter} onChange={setBucketFilter}
      options={data.instances.map(instance => ({ value: instance.instanceCode, label: instance.instanceCode }))} />
    {canManage && <Button type="primary" disabled={!data.instances.length} onClick={() => setDialog({ kind: 'bucket', instanceCode: bucketFilter })}>＋ 登记桶</Button>}</>)}
    <Table<BucketRow> rowKey={row => `${row.instanceCode}/${row.bucketName}`} tableLayout="fixed" dataSource={buckets} loading={data.loading}
      pagination={false} scroll={{ x: 1000 }} columns={[
        { title: '实例', width: 150, render: (_, row) => <span className="mono">{row.instanceCode}</span> },
        { title: '桶名', width: 220, render: (_, row) => <span className="mono">{row.bucketName}</span> },
        { title: '状态', width: 80, render: (_, row) => statusTag(row.status, bucketStatusLabels[row.status]) },
        { title: '最近检查', width: 260, render: (_, row) => row.lastCheckResult ? <div className="notification-refs">
          <span className={row.lastCheckResult === 'OK' ? 'notification-ok' : 'notification-missing'}>{checkResultLabels[row.lastCheckResult]}</span>
          <span className="muted">{date(row.lastCheckTime)}</span></div> : <span className="muted">未检查</span> },
        { title: '被规则引用', width: 100, render: (_, row) => row.routeCount },
        { title: '更新时间', width: 170, render: (_, row) => date(row.updateTime) },
        ...(canManage ? [{ title: '操作', width: 220, fixed: 'right' as const, render: (_: unknown, row: BucketRow) => <Space size={4} wrap>
          {row.status !== 'DISABLED' && <Button size="small" type="link" onClick={() => setDialog({ kind: 'check', bucket: row })}>检查</Button>}
          {bucketStatusActions(row).map(action => {
            const blocker = bucketStatusBlocker(row, action.target);
            return <Tooltip key={action.target} title={blocker}><Button size="small" type="link" danger={action.danger} disabled={!!blocker}
              onClick={() => setDialog({ kind: 'bucket-status', bucket: row, action })}>{action.verb}</Button></Tooltip>;
          })}
        </Space> }] : []),
      ]} /></section>;

  const routeTable = <section className="table-panel">{toolbar(`共 ${data.routes.length} 条规则`,
    canManage && <Button type="primary" onClick={() => setDialog({ kind: 'route', route: null })}>＋ 新建规则</Button>)}
    <Table<StorageRoute> rowKey="sourceSystem" tableLayout="fixed" dataSource={data.routes} loading={data.loading} pagination={false} scroll={{ x: 1000 }} columns={[
      { title: '来源系统', width: 200, render: (_, row) => deletableRoute(row) ? <span className="mono">{row.sourceSystem}</span> : <Tag color="blue">{sourceSystemLabel(row.sourceSystem)}</Tag> },
      { title: '实例', width: 150, render: (_, row) => <span className="mono">{row.instanceCode}</span> },
      { title: '桶', width: 200, render: (_, row) => <span className="mono">{row.bucketName}</span> },
      { title: '检查策略', width: 200, render: (_, row) => <span className="mono">{row.scanPolicyCode}</span> },
      { title: '上传期限', width: 100, render: (_, row) => `${row.maxUploadSeconds} 秒` },
      { title: '更新时间', width: 170, render: (_, row) => date(row.updateTime) },
      ...(canManage ? [{ title: '操作', width: 140, fixed: 'right' as const, render: (_: unknown, row: StorageRoute) => <Space size={4}>
        <Button size="small" type="link" onClick={() => setDialog({ kind: 'route', route: row })}>修改</Button>
        {deletableRoute(row) && <Button size="small" type="link" danger onClick={() => setDialog({ kind: 'route-delete', route: row })}>删除</Button>}
      </Space> }] : []),
    ]} /></section>;

  return <div className="configuration-page notification-page">
    <div className="page-header"><h1>存储管理</h1></div>
    <div className="notification-note">文件按「来源系统」的写入规则选择实例和桶；已上传的文件始终从原实例和桶读取。实例代码与桶名创建后不可修改；
      桶由基础设施预先创建，这里只登记和检查。密钥保存在 Vault，录入后无法查看，只能更换。{!canManage && '当前账号只有查看权限。'}</div>
    {data.failed && <Alert type="warning" showIcon title="存储配置暂时无法加载" action={<Button onClick={data.refresh}>重试</Button>} />}
    <Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'instances', label: '存储实例', children: instanceTable },
      { key: 'buckets', label: '桶', children: bucketTable },
      { key: 'routes', label: '写入规则', children: routeTable },
    ]} />
    {dialog?.kind === 'instance' && <InstanceDrawer instance={dialog.instance} {...commandProps} />}
    {dialog?.kind === 'credential' && <CredentialModal instance={dialog.instance} {...commandProps} />}
    {dialog?.kind === 'instance-status' && <StatusModal title={`${instanceStatusAction(dialog.instance).verb}存储实例 ${dialog.instance.instanceCode}`}
      path={`${instancePath(dialog.instance.instanceCode)}/status`} code={dialog.instance.instanceCode}
      target={instanceStatusAction(dialog.instance).target} version={dialog.instance.version} notice={instanceStatusNotice(dialog.instance)} {...commandProps} />}
    {dialog?.kind === 'bucket' && <BucketModal instances={data.instances} defaultInstance={dialog.instanceCode} {...commandProps} />}
    {dialog?.kind === 'check' && <BucketCheckModal instanceCode={dialog.bucket.instanceCode} bucketName={dialog.bucket.bucketName}
      onClose={() => { setDialog(null); data.refresh(); }} onDone={() => undefined} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'bucket-status' && <StatusModal title={`${dialog.action.verb}桶 ${dialog.bucket.bucketName}`}
      path={`${bucketPath(dialog.bucket.instanceCode, dialog.bucket.bucketName)}/status`}
      code={bucketObjectCode(dialog.bucket.instanceCode, dialog.bucket.bucketName)}
      target={dialog.action.target} version={dialog.bucket.version} notice={dialog.action.target === 'ACTIVE' ? '启用后可被写入规则引用并接受新上传。'
        : dialog.action.target === 'DISABLED' ? '停用后该桶上的文件读写都返回不可用，且不能再启用。' : '只读桶仍可下载，不接受新上传。'}
      {...commandProps} />}
    {dialog?.kind === 'route' && <RouteModal route={dialog.route} instances={data.instances} {...commandProps} />}
    {dialog?.kind === 'route-delete' && <DeleteRouteModal route={dialog.route} {...commandProps} />}
  </div>;
}
