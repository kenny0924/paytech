import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Empty, Spin, Table, Tabs, Tag, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { RefreshButton } from '../components/RefreshButton';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { byteSizeLabel, scanTypeLabel } from '../fileStorage/scanPolicy';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { Audit } from '../pages/Audit';
import {
  currentVersion, decodeFileRecordDetail, fileRecordDetailPath, objectStatusLabel, shortText, uploadKindLabel, uploadStatusLabel,
  versionDownloadable, versionStatusColors, versionStatusLabel,
} from './fileRecords';
import type { FileRecordDetail, FileScanRecord, FileVersionRecord } from './fileRecords';
import { LevelTag, PublicUrlBar, ScanTag, date } from './FileRecordParts';
import { useFileRecordDownload } from './useFileRecordDownload';

const account = (t: Translate, id: string | null) => id ? t('detail.account', { id: shortText(id) }) : '—';

/**
 * 文件记录详情（?file=&lt;id&gt;）：标题与操作、概要行、公开地址，其余分页签：概览、版本与检查、上传记录、下载与操作记录
 * <li>可下载的版本（当前或已替换且检查通过）在有下载按钮权限时显示下载。</li>
 * <li>最近下载嵌入审计组件，筛选 resourceType=FILE、resourceId=fileId；没有审计页面权限时不显示。</li>
 */
export function FileRecordDetailView({ fileId, canDownload, canAudit, onAuthorizationChanged }: {
  fileId: string; canDownload: boolean; canAudit: boolean; onAuthorizationChanged: () => void;
}) {
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification();
  const t = useT('file'), tCommon = useT('common'), tNav = useT('nav');
  const downloads = useFileRecordDownload(onAuthorizationChanged);
  const [detail, setDetail] = useState<FileRecordDetail>(); const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<'missing' | 'failed' | null>(null); const [reload, setReload] = useState(0);
  const [tab, setTab] = useState('overview');
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setFailure(null);
    session.request(fileRecordDetailPath(fileId), { signal: abort.signal }, value => decodeFileRecordDetail(value, fileId))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => {
        if (abort.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
        if (error instanceof ApiError && error.status === 404) { setFailure('missing'); return; }
        setFailure('failed'); notify(error, t('detail.loadFailed'));
      }).finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [fileId, reload, notify, onAuthorizationChanged]);
  function back() { const next = new URLSearchParams(location.search); next.delete('file'); void navigate(`?${next}`); }
  const heading = <div className="file-detail-crumb"><Button type="link" onClick={back}>← {tNav('resource.OPERATIONS_FILE_RECORDS')}</Button></div>;
  if (loading && !detail) return <div className="configuration-page">{heading}<div className="content-loading"><Spin /></div></div>;
  if (failure === 'missing') return <div className="configuration-page">{heading}<Empty description={t('detail.missing')} /></div>;
  if (failure || !detail) return <div className="configuration-page">{heading}
    <Alert type="warning" showIcon title={t('detail.unavailable')} action={<Button onClick={() => setReload(v => v + 1)}>{tCommon('action.retry')}</Button>} /></div>;
  const current = currentVersion(detail);
  const pendingUpload = detail.uploads.find(upload => upload.resultVersionNo === detail.pendingVersionNo);
  const versionsTab = <>
    <section className="table-panel"><div className="table-toolbar"><strong>{t('detail.versions.title')}</strong></div>
      <div className="muted file-detail-hint">{t('detail.versions.hint')}</div>
      <Table<FileVersionRecord> rowKey="versionNo" size="small" tableLayout="fixed" pagination={false} dataSource={detail.versions} scroll={{ x: 900 }} columns={[
        { title: t('records.column.version'), width: 60, render: (_, row) => `v${row.versionNo}` },
        { title: t('instances.column.status'), width: 80, render: (_, row) => <Tag color={versionStatusColors[row.versionStatus]}>{versionStatusLabel(row.versionStatus)}</Tag> },
        { title: t('records.column.scan'), width: 100, render: (_, row) => <ScanTag status={row.scanStatus} /> },
        { title: t('preview.sizeAndType'), width: 120, render: (_, row) => `${byteSizeLabel(row.sizeBytes)} · ${row.mimeType.split('/')[1]?.toUpperCase() ?? row.mimeType}` },
        { title: 'SHA-256', width: 110, render: (_, row) => <span className="mono" title={row.contentHash}>{shortText(row.contentHash)}</span> },
        { title: t('detail.versions.upload'), width: 260, render: (_, row) => <span className="cell-meta">{date(row.createTime)}
          {row.uploadId ? ' · ' + t('detail.versions.uploadId', { id: shortText(row.uploadId) }) : ''} · {account(t, row.uploaderAccountId)}</span> },
        { title: t('instances.column.actions'), width: 150, render: (_, row) => versionDownloadable(row, canDownload)
          ? <Button size="small" type="link" loading={downloads.isPending(fileId, row.versionNo)}
            onClick={() => void downloads.download(fileId, row.displayName, row.versionNo)}>{t('detail.versions.download')}</Button>
          : <span className="muted">{row.downloadable ? t('detail.versions.noPermission') : row.scanStatus === 'AVAILABLE' ? t('detail.versions.notDownloadable') : t('detail.versions.notScanned')}</span> },
      ]} /></section>
    <section className="table-panel"><div className="table-toolbar"><strong>{t('detail.scans.title')}</strong></div>
      <Table<FileScanRecord> rowKey={row => `${row.versionNo}/${row.scannerCode}/${row.attemptNo}`} size="small" tableLayout="fixed" pagination={false}
        dataSource={detail.scanResults} locale={{ emptyText: t('detail.scans.empty') }} scroll={{ x: 760 }} columns={[
          { title: t('records.column.version'), width: 60, render: (_, row) => `v${row.versionNo}` },
          { title: t('detail.scans.policyRevision'), width: 200, render: (_, row) => <span className="mono">{row.scanPolicyCode} r{row.revisionNo}</span> },
          { title: t('policy.column.scanner'), width: 180, render: (_, row) => <span><span className="mono">{row.scannerCode}</span>
            <div className="cell-meta">{scanTypeLabel(row.scanType)}</div></span> },
          { title: t('detail.scans.result'), width: 150, render: (_, row) => <span className="mono">{row.resultCode}</span> },
          { title: t('detail.scans.attempt'), width: 70, dataIndex: 'attemptNo' },
          { title: t('detail.scans.finishedAt'), width: 170, render: (_, row) => date(row.scannedTime) },
        ]} /></section>
  </>;
  const uploadsTab = <section className="file-detail-uploads">
    {detail.uploads.length ? <div className="file-upload-timeline">{detail.uploads.map(upload => <div key={upload.uploadId} className="file-upload-item">
      <div><strong>#{upload.kind === 'ORIGINAL' ? 1 : upload.replacementNo + 1} {uploadKindLabel(upload.kind)}</strong>
        {upload.resultVersionNo && <span> → v{upload.resultVersionNo}</span>}
        <Tag color={upload.status === 'COMPLETED' ? 'green' : upload.status === 'EXPIRED' || upload.status === 'CANCELLED' ? 'default' : 'gold'}
          style={{ marginLeft: 8 }}>{uploadStatusLabel(upload.status)}</Tag></div>
      <div className="cell-meta">{t('detail.uploads.times', { createdAt: date(upload.createTime), updatedAt: date(upload.updateTime), expiresAt: date(upload.expiresTime) })}</div>
      <div className="cell-meta">{upload.displayName} · {upload.expectedMime} · {byteSizeLabel(upload.expectedSize)} · {account(t, upload.uploaderAccountId)}</div>
    </div>)}</div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('detail.uploads.empty')} />}
    <div className="muted file-detail-hint">{t('detail.uploads.note')}</div>
  </section>;
  const overviewTab = <Descriptions bordered size="small" column={{ xs: 1, sm: 1, md: 2 }} className="file-detail-overview" items={[
    { key: 'fileId', label: t('detail.field.fileId'), children: <Typography.Text className="mono" copyable>{detail.fileId}</Typography.Text> },
    { key: 'effective', label: t('detail.field.effectiveAt'), children: current?.availableTime ? date(current.availableTime) : '—' },
    { key: 'source', label: t('detail.field.source'), children: <span className="mono">{detail.sourceSystem}</span> },
    { key: 'reason', label: t('detail.field.reason'), children: <span className="mono">{detail.reasonCode}</span> },
    { key: 'uploader', label: t('detail.field.uploader'), children: account(t, detail.uploaderAccountId) },
    { key: 'pending', label: t('detail.summary.pendingReplacement'), children: <>
      {detail.pendingVersionNo ? t('records.pendingVersion', { versionNo: detail.pendingVersionNo }) : t('detail.summary.none')}
      <div className="cell-meta">{pendingUpload ? t('detail.summary.pendingUploaded', { time: date(pendingUpload.updateTime) }) : t('detail.summary.singleReplacement')}</div></> },
    { key: 'instance', label: t('detail.field.instance'), children: <span className="mono">{detail.instanceCode}</span> },
    { key: 'bucket', label: t('detail.field.bucket'), children: <span className="mono">{detail.bucketName}</span> },
    { key: 'object', label: t('detail.summary.objectKey'), span: 'filled', children: <Typography.Text className="mono" copyable>{detail.objectRef}</Typography.Text> },
  ]} />;
  return <div className="configuration-page notification-page file-record-detail">
    {heading}
    <div className="file-detail-head">
      <div className="file-detail-title"><h1 className="file-ellipsis" title={detail.displayName}>{detail.displayName}</h1>
        <LevelTag level={detail.accessLevel} /><ScanTag status={detail.scanStatus} />
        {detail.pendingVersionNo && <Tag color="gold">{t('records.pendingVersion', { versionNo: detail.pendingVersionNo })}</Tag>}
        {detail.objectStatus !== 'ACTIVE' && <Tag color="red">{objectStatusLabel(detail.objectStatus)}</Tag>}</div>
      <div className="file-detail-actions">
        <RefreshButton onClick={() => setReload(v => v + 1)} loading={loading} />
        {canDownload && current?.downloadable && <Button type="primary" loading={downloads.isPending(fileId)}
          onClick={() => void downloads.download(fileId, detail.displayName)}>{t('detail.downloadCurrent', { versionNo: detail.currentVersionNo })}</Button>}
      </div>
    </div>
    <div className="file-detail-meta">
      <span>{t('records.currentVersion', { versionNo: detail.currentVersionNo })}</span>
      <span>{byteSizeLabel(detail.sizeBytes)}</span><span className="mono">{detail.mimeType}</span>
    </div>
    {detail.publicUrl && <PublicUrlBar publicUrl={detail.publicUrl} versionedUrl={detail.versionedUrl} versionNo={detail.currentVersionNo} />}
    {detail.accessLevel === 'PUBLIC' && !detail.publicUrl && <div className="notification-note">{t('detail.publicUrlPending')}</div>}
    <Tabs className="file-detail-tabs" activeKey={tab} onChange={setTab} items={[
      { key: 'overview', label: t('detail.tab.overview'), children: overviewTab },
      { key: 'versions', label: t('detail.tab.versions'), children: versionsTab },
      { key: 'uploads', label: t('detail.tab.uploads'), children: uploadsTab },
      { key: 'activity', label: t('detail.tab.activity'), children: canAudit
        ? <Audit key={`file-${fileId}`} objectFilter={{ resourceType: 'FILE', resourceId: fileId }} embedded />
        : <div className="muted">{t('detail.downloads.noAudit')}</div> },
    ]} />
  </div>;
}
