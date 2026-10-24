import { useEffect, useState } from 'react';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';
import { canOpenFileRecords, canOpenFileStorage, canUploadFile, decodeUploadConstraints, fileUploadPaths, pageLayout } from '../fileUpload/fileUpload';
import type { UploadConstraints } from '../fileUpload/fileUpload';
import { RecentUploads } from '../fileUpload/RecentUploads';
import { ConstraintsBar, UploadWorkspace } from '../fileUpload/UploadPanel';
import '../configuration/configuration.css';
import '../notification/notification.css';
import '../fileRecords/fileRecords.css';
import '../fileUpload/fileUpload.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/**
 * 文件上传：运营上传公开文件并取得公开地址
 * <li>页面加载只发限制提示与最近上传两个请求（Operations 路由每人并发上限 2）。</li>
 * <li>没有上传按钮时只读：只显示限制提示与最近上传；未配置公开写入规则时上传区禁用。</li>
 */
export function FileUpload({ resources, onAuthorizationChanged }: Props) {
  const tNav = useT('nav'), t = useT('file'); const notify = useErrorNotification();
  const [constraints, setConstraints] = useState<UploadConstraints | null>(null); const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0); const [recentReload, setRecentReload] = useState(0);
  const layout = pageLayout(canUploadFile(resources), constraints); const canOpenRecords = canOpenFileRecords(resources);
  useEffect(() => {
    const abort = new AbortController(); setFailed(false); setConstraints(null);
    session.request(fileUploadPaths.constraints, { signal: abort.signal }, decodeUploadConstraints)
      .then(value => { if (!abort.signal.aborted) setConstraints(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, t('upload.constraints.loadFailed')); } });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [reload, notify]);
  return <div className="configuration-page notification-page file-upload-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_FILE_UPLOAD')}</h1></div>
    <div className="notification-note">{t('upload.intro')}</div>
    <ConstraintsBar constraints={constraints} failed={failed} canOpenStorage={canOpenFileStorage(resources)} onRetry={() => setReload(value => value + 1)} />
    {layout.showUpload && <UploadWorkspace constraints={constraints} enabled={layout.uploadEnabled} canOpenRecords={canOpenRecords}
      onUploaded={() => setRecentReload(value => value + 1)} onAuthorizationChanged={onAuthorizationChanged} />}
    <RecentUploads reloadToken={recentReload} canOpenRecords={canOpenRecords} />
  </div>;
}
