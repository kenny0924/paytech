import { useEffect, useState } from 'react';
import { App, Button, Typography } from 'antd';
import { useT } from '../i18n/react';
import { revealVisibleSeconds } from './vault';

/** 复制到剪贴板并提示结果。 */
function useCopy() {
  const { message } = App.useApp(); const t = useT('vault');
  return (text: string) => navigator.clipboard.writeText(text)
    .then(() => { void message.success(t('copy.succeeded')); }, () => { void message.error(t('copy.failed')); });
}

/** 一次性展示的 Token：等宽显示并可复制。 */
export function TokenValue({ token }: { token: string }) {
  const copy = useCopy(); const t = useT('vault');
  return <div className="vault-result">
    <div className="vault-result-value">{token}</div>
    <div className="vault-result-actions"><Button type="link" size="small" onClick={() => void copy(token)}>{t('copy.action')}</Button></div>
  </div>;
}

/**
 * 敏感值展示：默认遮罩；点击“显示”后明文可见并倒计时，到期、切换页面或卸载时调用 onClear 由父组件清除原值。
 * 原值只在组件内存中，不写 URL、本地存储或日志。
 */
export function SensitiveValue({ value, onClear }: { value: string; onClear: () => void }) {
  const [visible, setVisible] = useState(false); const [remaining, setRemaining] = useState(revealVisibleSeconds);
  const copy = useCopy(); const t = useT('vault');
  useEffect(() => {
    if (!visible) return;
    if (remaining <= 0) { setVisible(false); onClear(); return; }
    const timer = window.setTimeout(() => setRemaining(seconds => seconds - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [visible, remaining, onClear]);
  useEffect(() => onClear, [onClear]);
  return <div className="vault-result">
    <div className="vault-result-value" aria-live="polite">{visible ? value : '•'.repeat(Math.min(Math.max(value.length, 8), 32))}</div>
    <div className="vault-result-actions">
      {visible && <Typography.Text type="secondary">{t('sensitive.countdown', { seconds: remaining })}</Typography.Text>}
      <Button type="link" size="small" onClick={() => { setRemaining(revealVisibleSeconds); setVisible(!visible); }}>{visible ? t('sensitive.hide') : t('sensitive.show')}</Button>
      <Button type="link" size="small" onClick={() => void copy(value)}>{t('copy.action')}</Button>
      <Button type="link" size="small" onClick={onClear}>{t('sensitive.clear')}</Button>
    </div>
  </div>;
}
