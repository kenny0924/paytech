import { useEffect, useMemo, useState } from 'react';
import { Empty, Select, Switch, Table, Typography, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { RefreshButton } from '../components/RefreshButton';
import { ScanTag, date } from '../fileRecords/FileRecordParts';
import { byteSizeLabel } from '../fileStorage/scanPolicy';
import { usePaging } from '../hooks/usePaging';
import { useT } from '../i18n/react';
import {
  decodeUploadedFilePage, fileRecordHref, fileUploadPaths, recentFilters, recentPageSizes, recentRangeDays, recentRangeLabel, uploaderLabel,
  visibleAddresses,
} from './fileUpload';
import type { RecentRangeDays, UploadedFile } from './fileUpload';

const filePage = decodeUploadedFilePage;

/**
 * 最近上传：本页面上传的公开文件，默认近 7 天；可只看我的
 * <li>一次只发一个列表请求，不按行补查。</li>
 * <li>上传完成后由页面递增 reloadToken 刷新。</li>
 * <li>有文件记录页面时，每行可跳到文件记录详情。</li>
 */
export function RecentUploads({ reloadToken, canOpenRecords }: { reloadToken: number; canOpenRecords: boolean }) {
  const t = useT('file'); const navigate = useNavigate();
  const [days, setDays] = useState<RecentRangeDays>(7); const [mine, setMine] = useState(false);
  const page = usePaging<UploadedFile>(fileUploadPaths.files, filePage, recentFilters(7, false));
  const rangeOptions = useMemo(() => recentRangeDays.map(value => ({ value, label: recentRangeLabel(value) })), [t]);
  useEffect(() => { if (reloadToken > 0) page.applyFilters(recentFilters(days, mine)); }, [reloadToken]);
  function change(nextDays: RecentRangeDays, nextMine: boolean) { setDays(nextDays); setMine(nextMine); page.applyFilters(recentFilters(nextDays, nextMine)); }
  return <section className="table-panel file-upload-recent">
    <div className="table-toolbar">
      <span className="table-count"><strong>{t('upload.recent.title')}</strong><span className="muted">{t('upload.recent.count', { count: page.data.total })}</span>
        <RefreshButton onClick={() => page.applyFilters(recentFilters(days, mine))} loading={page.loading} failed={!!page.error} /></span>
      <span className="file-upload-recent-filters">
        <Select<RecentRangeDays> size="small" value={days} options={rangeOptions} onChange={value => change(value, mine)} className="file-upload-range" />
        <label className="file-upload-mine"><Switch size="small" checked={mine} onChange={value => change(days, value)} />{t('upload.recent.mine')}</label>
      </span>
    </div>
    <Table<UploadedFile> rowKey="fileId" tableLayout="fixed" size="small" dataSource={page.error ? [] : page.data.items} loading={page.loading}
      scroll={{ x: 960 }} pagination={{ ...page.pagination, pageSizeOptions: recentPageSizes }}
      locale={{ emptyText: <Empty description={page.error ? t('upload.recent.loadFailed') : t('upload.recent.empty')} /> }} columns={[
        { title: t('upload.recent.column.name'), width: 260, render: (_, row) => <div><div className="file-ellipsis" title={row.displayName}>{row.displayName}</div>
          <div className="cell-meta">{byteSizeLabel(row.sizeBytes)} · <span className="mono" title={row.fileId}>{row.mimeType}</span></div></div> },
        { title: t('upload.recent.column.uploader'), width: 150, render: (_, row) => <span className={row.uploadedByMe ? undefined : 'mono'}>{uploaderLabel(row)}</span> },
        { title: t('upload.recent.column.uploadedAt'), width: 160, render: (_, row) => date(row.createTime) },
        { title: t('upload.recent.column.scan'), width: 110, render: (_, row) => <ScanTag status={row.scanStatus} /> },
        { title: t('upload.recent.column.publicUrl'), width: 240, render: (_, row) => {
          const addresses = visibleAddresses(row);
          return addresses ? <Typography.Text className="mono" ellipsis={{ tooltip: addresses.publicUrl }}
            copyable={{ text: addresses.publicUrl, tooltips: [t('check.copy'), t('check.copied')] }}>{addresses.publicUrl}</Typography.Text> : '—';
        } },
        ...(canOpenRecords ? [{ title: t('upload.recent.column.actions'), width: 130, render: (_: unknown, row: UploadedFile) =>
          <Button type="link" size="small" onClick={() => void navigate(fileRecordHref(row.fileId))}>{t('upload.recent.fileRecord')}</Button> }] : []),
      ]} />
  </section>;
}
