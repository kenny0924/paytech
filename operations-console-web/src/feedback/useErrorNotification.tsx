import { useCallback } from 'react';
import { App, Typography } from 'antd';
import { copyableErrorDetails, errorDetails, errorNotificationKey } from './errorDetails';

/** 由请求调用方显式展示错误，取消操作和旧会话响应保持静默。 */
export function useErrorNotification() {
  const { notification } = App.useApp();
  return useCallback((error: unknown, title = '请求未完成') => {
    const details = errorDetails(error);
    if (!details) return;
    notification.error({
      key: errorNotificationKey(details),
      title,
      description: <div className="error-notification-details">
        <p className="error-notification-message">{details.message}</p>
        <dl className="error-notification-metadata">
          <dt>code</dt><dd>{details.code}</dd>
          <dt>requestId</dt><dd>{details.requestId || '未返回'}</dd>
        </dl>
        <Typography.Text className="error-notification-copy" copyable={{
          text: copyableErrorDetails(details), format: 'text/plain', tabIndex: 0,
          icon: [<span key="copy">复制错误信息</span>, <span key="copied">已复制</span>],
          tooltips: ['复制错误信息', '已复制'],
        }} />
      </div>,
      icon: <span className="error-notification-icon" aria-hidden="true">!</span>,
      className: 'operations-error-notification',
      duration: 0,
      closable: { 'aria-label': '关闭错误通知' },
      role: 'alert',
    });
  }, [notification]);
}
