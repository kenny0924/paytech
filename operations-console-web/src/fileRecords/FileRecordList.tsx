import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Checkbox, Descriptions, Empty, Form, Input, Select, Space, Spin, Table } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import { RefreshButton } from '../components/RefreshButton';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { accessLevelLabel, accessLevels } from '../fileStorage/fileStorage';
import { byteSizeLabel } from '../fileStorage/scanPolicy';
import {
  activeQuickFilter, applyQuickFilter, decodeFileRecordDetail, decodeFileRecordPage, defaultRange, fileRecordDetailPath, fileRecordFilters,
  fileRecordPath, filtersFromQuery, formValuesFromFilters, maxRangeDays, previewableImage, quickFilters, scanStatusLabel, scanStatuses,
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
  const [src, setSrc] = useState<string>(); const [failed, setFailed] = useState(false); const t = useT('file');
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
  if (!eligible || failed) return <div className="file-preview-icon" aria-label={t('preview.fileIcon')}>{detail.mimeType.split('/')[1]?.toUpperCase() ?? 'FILE'}</div>;
  return <div className="file-preview-image">{src ? <img src={src} alt={detail.displayName} referrerPolicy="no-referrer" onError={() => setFailed(true)} /> : <Spin />}</div>;
}

/** 右侧快速预览：读取详情，展示摘要、公开地址与替换中提示。 */
function QuickPreview({ fileId, canDownload, onClose, onOpen, onAuthorizationChanged }: {
  fileId: string; canDownload: boolean; onClose: () => void; onOpen: () => void; onAuthorizationChanged: () => void;
}) {
  const notify = useErrorNotification(); const downloads = useFileRecordDownload(onAuthorizationChanged); const t = useT('file');
  const [detail, setDetail] = useState<FileRecordDetail>(); const [failed, setFailed] = useState(false);
  useEffect(() => {
    const abort = new AbortController(); setDetail(undefined); setFailed(false);
    session.request(fileRecordDetailPath(fileId), { signal: abort.signal }, value => decodeFileRecordDetail(value, fileId))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, t('detail.loadFailed')); } });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [fileId, notify]);
  return <aside className="file-quick-preview">
    <div className="file-quick-head"><strong className="file-ellipsis">{detail?.displayName ?? fileId}</strong>
      <Button size="small" type="text" aria-label={t('preview.close')} onClick={onClose}>✕</Button></div>
    {failed ? <Alert type="warning" showIcon title={t('detail.unavailable')} /> : !detail ? <Spin /> : <>
      <Space size={4} wrap><LevelTag level={detail.accessLevel} /><span>{t('records.currentVersion', { versionNo: detail.currentVersionNo })} · </span><ScanTag status={detail.scanStatus} />
        {detail.pendingVersionNo && <span className="notification-missing">{t('records.pendingVersion', { versionNo: detail.pendingVersionNo })}</span>}</Space>
      <ImagePreview detail={detail} canDownload={canDownload} />
      {detail.publicUrl && <PublicUrlBar publicUrl={detail.publicUrl} versionedUrl={detail.versionedUrl} versionNo={detail.currentVersionNo} compact />}
      <Descriptions column={1} size="small" items={[
        { key: 'id', label: t('records.field.fileId'), children: <span className="mono">{detail.fileId}</span> },
        { key: 'source', label: t('records.column.sourceAndReason'), children: <span>{detail.sourceSystem} · <span className="mono">{detail.reasonCode}</span></span> },
        { key: 'storage', label: t('detail.storage'), children: <span className="mono">{detail.instanceCode} / {detail.bucketName}</span> },
        { key: 'size', label: t('preview.sizeAndType'), children: `${byteSizeLabel(detail.sizeBytes)} · ${detail.mimeType}` },
        { key: 'uploader', label: t('preview.uploader'), children: detail.uploaderAccountId ? t('detail.account', { id: detail.uploaderAccountId }) : '—' },
        { key: 'time', label: t('preview.createdUpdated'), children: `${date(detail.createTime)} · ${date(detail.updateTime)}` },
      ]} />
      {detail.pendingVersionNo && <div className="notification-note">{detail.accessLevel === 'PUBLIC'
        ? t('preview.pendingPublic', { pendingNo: detail.pendingVersionNo, currentNo: detail.currentVersionNo })
        : t('preview.pendingPrivate', { pendingNo: detail.pendingVersionNo, currentNo: detail.currentVersionNo })}</div>}
      <Space className="file-quick-actions">
        {canDownload && detail.scanStatus === 'AVAILABLE' && <Button loading={downloads.isPending(detail.fileId)}
          onClick={() => void downloads.download(detail.fileId, detail.displayName)}>{t('preview.downloadCurrent')}</Button>}
        <Button type="primary" onClick={onOpen}>{t('preview.openDetail')}</Button>
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
  const t = useT('file'), tNav = useT('nav');
  const levelOptions = useMemo(() => accessLevels.map(value => ({ value, label: accessLevelLabel(value) })), [t]);
  const scanOptions = useMemo(() => scanStatuses.map(value => ({ value, label: scanStatusLabel(value) })), [t]);
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
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_FILE_RECORDS')}</h1></div>
    <div className="notification-note">{t('records.intro')}</div>
    <Form form={form} layout="vertical" onFinish={search} initialValues={formValuesFromFilters(filters)}><div className="file-records-search">
      <Form.Item name="timeRange" label={t('records.field.createdAt')} className="file-records-time" validateTrigger="onSubmit"
        rules={[{ validator: async (_, value) => { fileRecordFilters({ timeRange: value }); } }]}><TimeRangePicker /></Form.Item>
      <Form.Item name="sourceSystem" label={t('route.sourceSystem')}><Input allowClear maxLength={64} placeholder={t('records.field.all')} className="mono" /></Form.Item>
      <Form.Item name="reasonCode" label={t('records.field.reasonCode')}><Input allowClear maxLength={64} placeholder={t('records.field.exactMatch')} className="mono" /></Form.Item>
      <Form.Item name="accessLevel" label={t('bucket.accessLevel')}><Select allowClear placeholder={t('records.field.all')} options={levelOptions} /></Form.Item>
      <Form.Item name="scanStatus" label={t('records.field.scanStatus')}><Select allowClear placeholder={t('records.field.all')} options={scanOptions} /></Form.Item>
      <Form.Item name="fileId" label={t('records.field.fileId')}><Input allowClear maxLength={19} placeholder={t('records.field.exact')} className="mono" /></Form.Item>
      <Form.Item name="hasPendingReplacement" label=" " valuePropName="checked"><Checkbox>{t('records.field.hasPendingReplacement')}</Checkbox></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('records.search')}</Button>
        <Button onClick={() => { const values = { timeRange: defaultRange() }; form.resetFields(); form.setFieldsValue(values); page.applyFilters(fileRecordFilters(values)); }}>{t('records.reset')}</Button></div>
    </div></Form>
    <div className="file-quick-filters"><span className="muted">{t('records.quickLabel')}</span>{quickFilters.map(item => <Button key={item.key} size="small"
      type={quick === item.key ? 'primary' : 'default'} onClick={() => page.applyFilters(applyQuickFilter(filters, item.key))}>{enumLabel(t, 'records.quick', item.key)}</Button>)}</div>
    <div className={selected ? 'file-records-layout has-preview' : 'file-records-layout'}>
      <section className="table-panel"><div className="table-toolbar"><span className="table-count"><span>{t('records.count', { count: page.data.total })}</span>
        <RefreshButton onClick={page.refresh} loading={page.loading} failed={!!page.error} /></span>
        <span className="muted">{t('records.sortNote', { days: maxRangeDays })}</span></div>
        <Table<FileRecordSummary> rowKey="fileId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1060 }}
          pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }}
          locale={{ emptyText: page.error ? <Empty description={t('records.loadFailed')} /> : <Empty description={t('records.empty')} /> }}
          rowClassName={row => row.fileId === selected ? 'is-selected clickable-row' : 'clickable-row'}
          onRow={row => ({ onClick: () => setSelected(row.fileId) })} columns={[
            { title: t('records.column.file'), width: 250, render: (_, row) => <div><div className="file-ellipsis" title={row.displayName}>{row.displayName}</div>
              <div className="cell-meta mono">{row.fileId}</div></div> },
            { title: t('records.column.sourceAndReason'), width: 230, render: (_, row) => <div><div>{row.sourceSystem}</div><div className="cell-meta mono">{row.reasonCode}</div></div> },
            { title: t('records.column.access'), width: 80, render: (_, row) => <LevelTag level={row.accessLevel} /> },
            { title: t('records.column.version'), width: 120, render: (_, row) => <VersionBadge current={row.currentVersionNo} pending={row.pendingVersionNo} /> },
            { title: t('records.column.scan'), width: 100, render: (_, row) => <ScanTag status={row.scanStatus} /> },
            { title: t('records.column.size'), width: 90, render: (_, row) => byteSizeLabel(row.sizeBytes) },
            { title: t('records.field.createdAt'), width: 170, render: (_, row) => date(row.createTime) },
          ]} /></section>
      {selected && <QuickPreview key={selected} fileId={selected} canDownload={canDownload} onClose={() => setSelected(undefined)}
        onOpen={() => open(selected)} onAuthorizationChanged={onAuthorizationChanged} />}
    </div>
  </div>;
}
