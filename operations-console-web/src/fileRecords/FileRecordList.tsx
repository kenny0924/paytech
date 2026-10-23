import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Descriptions, Empty, Form, Input, Select, Space, Spin, Table } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import { RefreshButton } from '../components/RefreshButton';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { usePaging } from '../hooks/usePaging';
import { accessLevelLabels, accessLevels } from '../fileStorage/fileStorage';
import { byteSizeLabel } from '../fileStorage/scanPolicy';
import {
  activeQuickFilter, applyQuickFilter, decodeFileRecordDetail, decodeFileRecordPage, defaultRange, fileRecordDetailPath, fileRecordFilters,
  fileRecordPath, filtersFromQuery, formValuesFromFilters, maxRangeDays, previewableImage, quickFilters, scanStatusLabels, scanStatuses,
} from './fileRecords';
import type { FileRecordDetail, FileRecordFilterValues, FileRecordSummary } from './fileRecords';
import { LevelTag, PublicUrlBar, ScanTag, VersionBadge, date } from './FileRecordParts';
import { authorizeFileDownload, useFileRecordDownload } from './useFileRecordDownload';

const filePage = decodeFileRecordPage;

/**
 * 图片预览：只在有下载权限时加载
 * <li>公开文件用带版本的公开地址，不产生下载授权。</li>
 * <li>私有文件申请一次当前版本的下载授权（写下载审计），地址只用于本次显示。</li>
 */
function ImagePreview({ detail, canDownload }: { detail: FileRecordDetail; canDownload: boolean }) {
  const [src, setSrc] = useState<string>(); const [failed, setFailed] = useState(false);
  const eligible = canDownload && previewableImage(detail.mimeType) && detail.scanStatus === 'AVAILABLE';
  useEffect(() => {
    setSrc(undefined); setFailed(false);
    if (!eligible) return;
    if (detail.versionedUrl ?? detail.publicUrl) { setSrc((detail.versionedUrl ?? detail.publicUrl)!); return; }
    const abort = new AbortController();
    authorizeFileDownload(detail.fileId, undefined, abort.signal)
      .then(grant => { if (!abort.signal.aborted) setSrc(grant.url); })
      .catch(() => { if (!abort.signal.aborted) setFailed(true); });
    return () => abort.abort();
  }, [detail.fileId, detail.versionedUrl, detail.publicUrl, eligible]);
  if (!eligible || failed) return <div className="file-preview-icon" aria-label="文件图标">{detail.mimeType.split('/')[1]?.toUpperCase() ?? 'FILE'}</div>;
  return <div className="file-preview-image">{src ? <img src={src} alt={detail.displayName} referrerPolicy="no-referrer" onError={() => setFailed(true)} /> : <Spin />}</div>;
}

/** 右侧快速预览：读取详情，展示摘要、公开地址与替换中提示。 */
function QuickPreview({ fileId, canDownload, onClose, onOpen, onAuthorizationChanged }: {
  fileId: string; canDownload: boolean; onClose: () => void; onOpen: () => void; onAuthorizationChanged: () => void;
}) {
  const notify = useErrorNotification(); const downloads = useFileRecordDownload(onAuthorizationChanged);
  const [detail, setDetail] = useState<FileRecordDetail>(); const [failed, setFailed] = useState(false);
  useEffect(() => {
    const abort = new AbortController(); setDetail(undefined); setFailed(false);
    session.request(fileRecordDetailPath(fileId), { signal: abort.signal }, value => decodeFileRecordDetail(value, fileId))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, '文件详情加载失败'); } });
    return () => abort.abort();
  }, [fileId, notify]);
  return <aside className="file-quick-preview">
    <div className="file-quick-head"><strong className="file-ellipsis">{detail?.displayName ?? fileId}</strong>
      <Button size="small" type="text" aria-label="关闭预览" onClick={onClose}>✕</Button></div>
    {failed ? <Alert type="warning" showIcon title="文件详情暂时无法加载" /> : !detail ? <Spin /> : <>
      <Space size={4} wrap><LevelTag level={detail.accessLevel} /><span>当前 v{detail.currentVersionNo} · </span><ScanTag status={detail.scanStatus} />
        {detail.pendingVersionNo && <span className="notification-missing">v{detail.pendingVersionNo} 检查中</span>}</Space>
      <ImagePreview detail={detail} canDownload={canDownload} />
      {detail.publicUrl && <PublicUrlBar publicUrl={detail.publicUrl} versionedUrl={detail.versionedUrl} versionNo={detail.currentVersionNo} compact />}
      <Descriptions column={1} size="small" items={[
        { key: 'id', label: '文件 ID', children: <span className="mono">{detail.fileId}</span> },
        { key: 'source', label: '来源 / 原因', children: <span>{detail.sourceSystem} · <span className="mono">{detail.reasonCode}</span></span> },
        { key: 'storage', label: '存储', children: <span className="mono">{detail.instanceCode} / {detail.bucketName}</span> },
        { key: 'size', label: '大小 / 类型', children: `${byteSizeLabel(detail.sizeBytes)} · ${detail.mimeType}` },
        { key: 'uploader', label: '上传人', children: detail.uploaderAccountId ? `账号 ${detail.uploaderAccountId}` : '—' },
        { key: 'time', label: '创建 / 更新', children: `${date(detail.createTime)} · ${date(detail.updateTime)}` },
      ]} />
      {detail.pendingVersionNo && <div className="notification-note">v{detail.pendingVersionNo} 已上传、格式检查中。
        {detail.accessLevel === 'PUBLIC' ? `检查通过前，公开地址可能已经返回 v${detail.pendingVersionNo} 的内容；未通过时会自动恢复为 v${detail.currentVersionNo}。`
          : `检查通过前下载仍得到 v${detail.currentVersionNo}。`}</div>}
      <Space className="file-quick-actions">
        {canDownload && detail.scanStatus === 'AVAILABLE' && <Button loading={downloads.isPending(detail.fileId)}
          onClick={() => void downloads.download(detail.fileId, detail.displayName)}>下载当前版本</Button>}
        <Button type="primary" onClick={onOpen}>查看详情 →</Button>
      </Space>
    </>}
  </aside>;
}

/**
 * 文件记录列表：一行筛选（时间范围必填，默认近 7 天，最长 31 天）+ 快捷筛选，筛选与分页写入 URL
 * <li>点击行打开右侧快速预览；「查看详情」进入 ?file=&lt;id&gt; 详情。</li>
 * <li>快捷筛选不显示计数（没有统计接口）。</li>
 */
export function FileRecordList({ canDownload, onAuthorizationChanged }: { canDownload: boolean; onAuthorizationChanged: () => void }) {
  const location = useLocation(); const navigate = useNavigate(); const [form] = Form.useForm<FileRecordFilterValues>();
  const query = new URLSearchParams(location.search);
  const [filters] = useState(() => filtersFromQuery(query));
  const page = usePaging<FileRecordSummary>(fileRecordPath, filePage, filters, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 20) },
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`, { replace: true }); },
  });
  const [selected, setSelected] = useState<string>();
  const quick = activeQuickFilter(filters);
  function search(values: FileRecordFilterValues) {
    if (values.timeRange) form.setFieldValue('timeRange', freezeTimeRange(values.timeRange));
    page.applyFilters(fileRecordFilters(values));
  }
  function open(fileId: string) { const next = new URLSearchParams(location.search); next.set('file', fileId); void navigate(`?${next}`); }
  return <div className="configuration-page notification-page file-records-page">
    <div className="page-heading"><h1>文件记录</h1></div>
    <div className="notification-note">查询各系统上传到平台的文件、版本和检查结果。只读：上传、替换由业务系统完成；下载会记录审计。</div>
    <Form form={form} layout="vertical" onFinish={search} initialValues={formValuesFromFilters(filters)}><div className="file-records-search">
      <Form.Item name="timeRange" label="创建时间" className="file-records-time" validateTrigger="onSubmit"
        rules={[{ validator: async (_, value) => { fileRecordFilters({ timeRange: value }); } }]}><TimeRangePicker /></Form.Item>
      <Form.Item name="sourceSystem" label="来源系统"><Input allowClear maxLength={64} placeholder="全部" className="mono" /></Form.Item>
      <Form.Item name="reasonCode" label="原因代码"><Input allowClear maxLength={64} placeholder="精确匹配" className="mono" /></Form.Item>
      <Form.Item name="accessLevel" label="访问级别"><Select allowClear placeholder="全部" options={accessLevels.map(value => ({ value, label: accessLevelLabels[value] }))} /></Form.Item>
      <Form.Item name="scanStatus" label="检查状态"><Select allowClear placeholder="全部" options={scanStatuses.map(value => ({ value, label: scanStatusLabels[value] }))} /></Form.Item>
      <Form.Item name="fileId" label="文件 ID"><Input allowClear maxLength={19} placeholder="精确" className="mono" /></Form.Item>
      <Form.Item name="hasPendingReplacement" label=" " valuePropName="checked"><Checkbox>有替换进行中</Checkbox></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button>
        <Button onClick={() => { const values = { timeRange: defaultRange() }; form.resetFields(); form.setFieldsValue(values); page.applyFilters(fileRecordFilters(values)); }}>重置</Button></div>
    </div></Form>
    <div className="file-quick-filters"><span className="muted">快捷：</span>{quickFilters.map(item => <Button key={item.key} size="small"
      type={quick === item.key ? 'primary' : 'default'} onClick={() => page.applyFilters(applyQuickFilter(filters, item.key))}>{item.label}</Button>)}</div>
    <div className={selected ? 'file-records-layout has-preview' : 'file-records-layout'}>
      <section className="table-panel"><div className="table-toolbar"><span className="table-count"><span>共 {page.data.total} 个文件</span>
        <RefreshButton onClick={page.refresh} loading={page.loading} failed={!!page.error} /></span>
        <span className="muted">按创建时间倒序 · 时间范围最长 {maxRangeDays} 天</span></div>
        <Table<FileRecordSummary> rowKey="fileId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1060 }}
          pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }}
          locale={{ emptyText: page.error ? <Empty description="文件记录暂时无法加载，请重试" /> : <Empty description="该时间范围内没有符合条件的文件" /> }}
          rowClassName={row => row.fileId === selected ? 'is-selected clickable-row' : 'clickable-row'}
          onRow={row => ({ onClick: () => setSelected(row.fileId) })} columns={[
            { title: '文件', width: 250, render: (_, row) => <div><div className="file-ellipsis" title={row.displayName}>{row.displayName}</div>
              <div className="cell-meta mono">{row.fileId}</div></div> },
            { title: '来源 / 原因', width: 230, render: (_, row) => <div><div>{row.sourceSystem}</div><div className="cell-meta mono">{row.reasonCode}</div></div> },
            { title: '访问', width: 80, render: (_, row) => <LevelTag level={row.accessLevel} /> },
            { title: '版本', width: 120, render: (_, row) => <VersionBadge current={row.currentVersionNo} pending={row.pendingVersionNo} /> },
            { title: '检查', width: 100, render: (_, row) => <ScanTag status={row.scanStatus} /> },
            { title: '大小', width: 90, render: (_, row) => byteSizeLabel(row.sizeBytes) },
            { title: '创建时间', width: 170, render: (_, row) => date(row.createTime) },
          ]} /></section>
      {selected && <QuickPreview key={selected} fileId={selected} canDownload={canDownload} onClose={() => setSelected(undefined)}
        onOpen={() => open(selected)} onAuthorizationChanged={onAuthorizationChanged} />}
    </div>
  </div>;
}
