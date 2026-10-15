import { useEffect, useState } from 'react';
import { App, Button, Typography } from 'antd';
import { revealVisibleSeconds } from './vault';

/** 复制到剪贴板并提示结果。 */
function useCopy() {
  const { message } = App.useApp();
  return (text: string) => navigator.clipboard.writeText(text)
    .then(() => { void message.success('已复制'); }, () => { void message.error('复制失败，请手动选择复制'); });
}

/** 一次性展示的 Token：等宽显示并可复制。 */
export function TokenValue({ token }: { token: string }) {
  const copy = useCopy();
  return <div className="vault-result">
    <div className="vault-result-value">{token}</div>
    <div className="vault-result-actions"><Button type="link" size="small" onClick={() => void copy(token)}>复制</Button></div>
  </div>;
}

/**
 * 敏感值展示：默认遮罩；点击“显示”后明文可见并倒计时，到期、切换页面或卸载时调用 onClear 由父组件清除原值。
 * 原值只在组件内存中，不写 URL、本地存储或日志。
 */
export function SensitiveValue({ value, onClear }: { value: string; onClear: () => void }) {
  const [visible, setVisible] = useState(false); const [remaining, setRemaining] = useState(revealVisibleSeconds);
  const copy = useCopy();
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
      {visible && <Typography.Text type="secondary">{remaining} 秒后自动隐藏并清除</Typography.Text>}
      <Button type="link" size="small" onClick={() => { setRemaining(revealVisibleSeconds); setVisible(!visible); }}>{visible ? '隐藏' : '显示'}</Button>
      <Button type="link" size="small" onClick={() => void copy(value)}>复制</Button>
      <Button type="link" size="small" onClick={onClear}>清除</Button>
    </div>
  </div>;
}
