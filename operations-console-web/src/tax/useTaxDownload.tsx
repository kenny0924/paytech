import { useCallback, useRef, useState } from 'react';
import { App, Button, Typography } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';
import { decodeTaxDownload, shortRequestId, taxPaths } from './tax';

const failureKey = 'tax-receipt-download-failed';

/**
 * 税票下载：每次点击向 BFF 申请最长 60 秒的地址，随即以附件方式下载；地址不保存、不展示
 * <li>403 刷新导航权限；400 / 404 / 409（税票未开具或不属于该订单）按统一错误提示。</li>
 * <li>文件服务不可用等 5xx 与网络失败（原型 21）：右上角错误提示条说明税票本身不受影响，提供重试下载与复制请求编号，可关闭。</li>
 */
export function useTaxDownload(orderNo: string, onAuthorizationChanged: () => void) {
  const t = useT('tax');
  const { notification } = App.useApp();
  const notify = useErrorNotification();
  const [pending, setPending] = useState(false);
  const busy = useRef(false);
  const open = useCallback(async (documentId: string) => {
    if (busy.current) return;
    busy.current = true; setPending(true);
    notification.destroy(failureKey);
    try {
      // 无请求体的 POST 仍按 BFF 写入口要求声明 JSON 内容类型。
      const grant = await session.request(taxPaths.download(orderNo, documentId), { method: 'POST', headers: { 'Content-Type': 'application/json' } },
        value => decodeTaxDownload(value, documentId));
      const link = document.createElement('a');
      link.href = grant.url; link.rel = 'noopener noreferrer'; link.download = `${grant.documentNumber ?? documentId}.pdf`;
      document.body.appendChild(link); link.click(); link.remove();
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') return;
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
        notify(error, t('download.notAvailable'));
        return;
      }
      const requestId = error instanceof ApiError ? error.requestId.trim() : '';
      notification.error({
        key: failureKey, title: t('download.failedTitle'), duration: 0, closable: { 'aria-label': t('download.close') },
        className: 'operations-error-notification tax-download-failure', role: 'alert',
        description: <div className="tax-download-failure-body">
          <p>{t('download.failedBody')}</p>
          <div className="tax-download-failure-actions">
            <Button type="link" size="small" onClick={() => { notification.destroy(failureKey); void open(documentId); }}>{t('download.retry')}</Button>
            {requestId && <Typography.Text type="secondary" copyable={{ text: requestId, format: 'text/plain',
              icon: [<span key="copy">{t('download.copyRequestId', { id: shortRequestId(requestId) })}</span>, <span key="copied">{t('download.copied')}</span>],
              tooltips: [false, false] }} />}
          </div>
        </div>,
      });
    } finally {
      busy.current = false; setPending(false);
    }
  }, [notification, notify, onAuthorizationChanged, orderNo, t]);
  return { pending, open };
}
