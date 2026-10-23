import { useCallback } from 'react';
import { App, Typography } from 'antd';
import { useT } from '../i18n/react';
import { copyableErrorDetails, errorDetails, errorNotificationKey } from './errorDetails';

/** 由请求调用方显式展示错误，取消操作和旧会话响应保持静默。 */
export function useErrorNotification() {
  const { notification } = App.useApp();
  const t = useT('common');
  return useCallback((error: unknown, title = t('error.defaultTitle')) => {
    const details = errorDetails(error);
    if (!details) return;
    notification.error({
      key: errorNotificationKey(details),
      title,
      description: <div className="error-notification-details">
        <p className="error-notification-message">{details.message}</p>
        <dl className="error-notification-metadata">
          <dt>{t('error.code')}</dt><dd>{details.code}</dd>
          <dt>{t('error.requestId')}</dt><dd>{details.requestId || t('error.notReturned')}</dd>
        </dl>
        <Typography.Text className="error-notification-copy" copyable={{
          text: copyableErrorDetails(details), format: 'text/plain', tabIndex: 0,
          icon: [<span key="copy">{t('error.copy')}</span>, <span key="copied">{t('error.copied')}</span>],
          tooltips: [t('error.copy'), t('error.copied')],
        }} />
      </div>,
      icon: <span className="error-notification-icon" aria-hidden="true">!</span>,
      className: 'operations-error-notification',
      duration: 0,
      closable: { 'aria-label': t('error.close') },
      role: 'alert',
    });
  }, [notification, t]);
}
