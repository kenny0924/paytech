import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Empty, Space, Spin, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { RefreshButton } from '../components/RefreshButton';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { byteSizeLabel, scanTypeLabels } from '../fileStorage/scanPolicy';
import { Audit } from '../pages/Audit';
import {
  currentVersion, decodeFileRecordDetail, fileRecordDetailPath, objectStatusLabels, shortText, uploadKindLabels, uploadStatusLabels,
  versionDownloadable, versionStatusColors, versionStatusLabels,
} from './fileRecords';
import type { FileRecordDetail, FileScanRecord, FileVersionRecord } from './fileRecords';
import { LevelTag, PublicUrlBar, ScanTag, date } from './FileRecordParts';
import { useFileRecordDownload } from './useFileRecordDownload';

const account = (id: string | null) => id ? `账号 ${shortText(id)}` : '—';

/** 摘要卡片的一格。 */
function SummaryCard({ label, value, meta }: { label: string; value: ReactNode; meta: ReactNode }) {
  return <div className="file-summary-card"><div className="muted">{label}</div><div className="file-summary-value">{value}</div><div className="cell-meta">{meta}</div></div>;
}

/**
 * 文件记录详情（?file=&lt;id&gt;）：摘要、公开地址条、版本历史、检查结果、上传记录与最近下载
 * <li>可下载的版本（当前或已替换且检查通过）在有下载按钮权限时显示下载。</li>
 * <li>最近下载嵌入审计组件，筛选 resourceType=FILE、resourceId=fileId；没有审计页面权限时不显示。</li>
 */
export function FileRecordDetailView({ fileId, canDownload, canAudit, onAuthorizationChanged }: {
  fileId: string; canDownload: boolean; canAudit: boolean; onAuthorizationChanged: () => void;
}) {
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification();
  const downloads = useFileRecordDownload(onAuthorizationChanged);
  const [detail, setDetail] = useState<FileRecordDetail>(); const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<'missing' | 'failed' | null>(null); const [reload, setReload] = useState(0);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setFailure(null);
    session.request(fileRecordDetailPath(fileId), { signal: abort.signal }, value => decodeFileRecordDetail(value, fileId))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => {
        if (abort.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
        if (error instanceof ApiError && error.status === 404) { setFailure('missing'); return; }
        setFailure('failed'); notify(error, '文件详情加载失败');
      }).finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [fileId, reload, notify, onAuthorizationChanged]);
  function back() { const next = new URLSearchParams(location.search); next.delete('file'); void navigate(`?${next}`); }
  const heading = <div className="file-detail-crumb"><Button type="link" onClick={back}>← 文件记录</Button><span className="muted">/</span>
    <span className="mono">{fileId}</span></div>;
  if (loading && !detail) return <div className="configuration-page">{heading}<div className="content-loading"><Spin /></div></div>;
  if (failure === 'missing') return <div className="configuration-page">{heading}<Empty description="文件不存在" /></div>;
  if (failure || !detail) return <div className="configuration-page">{heading}
    <Alert type="warning" showIcon title="文件详情暂时无法加载" action={<Button onClick={() => setReload(v => v + 1)}>重试</Button>} /></div>;
  const current = currentVersion(detail);
  const pendingUpload = detail.uploads.find(upload => upload.resultVersionNo === detail.pendingVersionNo);
  return <div className="configuration-page notification-page file-record-detail">
    <div className="file-detail-top">{heading}<Space>
      <span className="table-count"><span className="muted">刷新</span><RefreshButton onClick={() => setReload(v => v + 1)} loading={loading} /></span>
      {canDownload && current?.downloadable && <Button type="primary" loading={downloads.isPending(fileId)}
        onClick={() => void downloads.download(fileId, detail.displayName)}>下载当前版本 v{detail.currentVersionNo}</Button>}
    </Space></div>
    <div className="file-detail-title"><h1 className="file-ellipsis" title={detail.displayName}>{detail.displayName}</h1>
      <LevelTag level={detail.accessLevel} /><span>当前 v{detail.currentVersionNo} · </span><ScanTag status={detail.scanStatus} />
      {detail.pendingVersionNo && <Tag color="gold">v{detail.pendingVersionNo} 检查中</Tag>}
      {detail.objectStatus !== 'ACTIVE' && <Tag color="red">{objectStatusLabels[detail.objectStatus]}</Tag>}</div>
    <div className="file-summary">
      <SummaryCard label="当前版本" value={`v${detail.currentVersionNo}`}
        meta={`${current?.availableTime ? `${date(current.availableTime)} 生效 · ` : ''}${byteSizeLabel(detail.sizeBytes)} · ${detail.mimeType}`} />
      <SummaryCard label="进行中的替换" value={detail.pendingVersionNo ? `v${detail.pendingVersionNo} 检查中` : '无'}
        meta={pendingUpload ? `${date(pendingUpload.updateTime)} 完成上传 · 等待格式检查` : '同一文件同时只允许一个替换'} />
      <SummaryCard label="来源 / 原因" value={detail.sourceSystem} meta={<><span className="mono">{detail.reasonCode}</span> · 上传人 {account(detail.uploaderAccountId)}</>} />
      <SummaryCard label="存储" value={<span className="mono">{detail.instanceCode}</span>}
        meta={<><span className="mono">{detail.bucketName}</span> · 对象键 <span className="mono" title={detail.objectRef}>{shortText(detail.objectRef, 8, 0)}</span></>} />
    </div>
    {detail.publicUrl && <PublicUrlBar publicUrl={detail.publicUrl} versionedUrl={detail.versionedUrl} versionNo={detail.currentVersionNo} />}
    {detail.accessLevel === 'PUBLIC' && !detail.publicUrl && <div className="notification-note">公开文件在当前版本检查通过后才给出公开地址。</div>}
    <div className="file-detail-grid">
      <div>
        <section className="table-panel"><div className="table-toolbar"><strong>版本历史</strong>
          <span className="muted">同一文件地址下的每次内容；存储的非当前版本过期后将无法下载</span></div>
          <Table<FileVersionRecord> rowKey="versionNo" size="small" tableLayout="fixed" pagination={false} dataSource={detail.versions} scroll={{ x: 900 }} columns={[
            { title: '版本', width: 60, render: (_, row) => `v${row.versionNo}` },
            { title: '状态', width: 80, render: (_, row) => <Tag color={versionStatusColors[row.versionStatus]}>{versionStatusLabels[row.versionStatus]}</Tag> },
            { title: '检查', width: 90, render: (_, row) => <ScanTag status={row.scanStatus} /> },
            { title: '大小 / 类型', width: 130, render: (_, row) => `${byteSizeLabel(row.sizeBytes)} · ${row.mimeType.split('/')[1]?.toUpperCase() ?? row.mimeType}` },
            { title: 'SHA-256', width: 110, render: (_, row) => <span className="mono" title={row.contentHash}>{shortText(row.contentHash)}</span> },
            { title: '上传', width: 260, render: (_, row) => <span className="cell-meta">{date(row.createTime)}
              {row.uploadId ? ` · 上传 ${shortText(row.uploadId)}` : ''} · {account(row.uploaderAccountId)}</span> },
            { title: '操作', width: 130, render: (_, row) => versionDownloadable(row, canDownload)
              ? <Button size="small" type="link" loading={downloads.isPending(fileId, row.versionNo)}
                onClick={() => void downloads.download(fileId, row.displayName, row.versionNo)}>下载</Button>
              : <span className="muted">{row.downloadable ? '无下载权限' : row.scanStatus === 'AVAILABLE' ? '不可下载' : '检查中或未通过，不能下载'}</span> },
          ]} /></section>
        <section className="table-panel"><div className="table-toolbar"><strong>检查结果</strong></div>
          <Table<FileScanRecord> rowKey={row => `${row.versionNo}/${row.scannerCode}/${row.attemptNo}`} size="small" tableLayout="fixed" pagination={false}
            dataSource={detail.scanResults} locale={{ emptyText: '还没有检查结果' }} scroll={{ x: 760 }} columns={[
              { title: '版本', width: 60, render: (_, row) => `v${row.versionNo}` },
              { title: '策略修订', width: 220, render: (_, row) => <span className="mono">{row.scanPolicyCode} r{row.revisionNo}</span> },
              { title: '检查器', width: 160, render: (_, row) => <span><span className="mono">{row.scannerCode}</span>
                <div className="cell-meta">{scanTypeLabels[row.scanType] ?? row.scanType}</div></span> },
              { title: '结果', width: 140, render: (_, row) => <span className="mono">{row.resultCode}</span> },
              { title: '尝试', width: 60, dataIndex: 'attemptNo' },
              { title: '完成时间', width: 170, render: (_, row) => date(row.scannedTime) },
            ]} /></section>
      </div>
      <div>
        <section className="table-panel"><div className="table-toolbar"><strong>上传记录</strong></div>
          {detail.uploads.length ? <div className="file-upload-timeline">{detail.uploads.map(upload => <div key={upload.uploadId} className="file-upload-item">
            <div><strong>#{upload.kind === 'ORIGINAL' ? 1 : upload.replacementNo + 1} {uploadKindLabels[upload.kind]}</strong>
              {upload.resultVersionNo && <span> → v{upload.resultVersionNo}</span>}
              <Tag color={upload.status === 'COMPLETED' ? 'green' : upload.status === 'EXPIRED' || upload.status === 'CANCELLED' ? 'default' : 'gold'}
                style={{ marginLeft: 8 }}>{uploadStatusLabels[upload.status] ?? upload.status}</Tag></div>
            <div className="cell-meta">{date(upload.createTime)} 开始 · {date(upload.updateTime)} 更新 · 期限 {date(upload.expiresTime)}</div>
            <div className="cell-meta">{upload.displayName} · {upload.expectedMime} · {byteSizeLabel(upload.expectedSize)} · {account(upload.uploaderAccountId)}</div>
          </div>)}</div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有上传记录（非上传来源的文件）" />}
          <div className="notification-note">同一文件同时只允许一个替换：上传未完成或新版本检查结束前，新的替换请求会被拒绝（409）。</div>
        </section>
        <section className="file-recent-downloads"><h2>最近下载</h2>
          {canAudit ? <Audit key={`file-${fileId}`} objectFilter={{ resourceType: 'FILE', resourceId: fileId }} embedded />
            : <div className="muted">下载记录在审计日志中（对象类型「文件」）；当前账号没有审计日志页面权限。</div>}</section>
      </div>
    </div>
  </div>;
}
