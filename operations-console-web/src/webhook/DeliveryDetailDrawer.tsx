import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Spin, Tag } from 'antd';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import {
  attemptStatusLabels, decodeDeliveryDetail, deliveryStatusColors, deliveryStatusLabels, failureText, isReplayable, newestAttemptsFirst,
  webhookAdminPath,
} from './webhook';
import type { DeliveryAttempt, DeliveryDetail, DeliverySummary } from './webhook';

const date = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
/** 默认展示的最近尝试条数，其余可展开。 */
const collapsedAttempts = 3;

/** 投递详情抽屉：基本信息、冻结的 Envelope、尝试记录（最新在前）；有维护权限且已结束时可 Replay。 */
export function DeliveryDetailDrawer({ deliveryId, endpointUrl, canManage, onReplay, onClose }: {
  deliveryId: string; endpointUrl?: string; canManage: boolean; onReplay: (delivery: DeliverySummary) => void; onClose: () => void;
}) {
  const notify = useErrorNotification();
  const [detail, setDetail] = useState<DeliveryDetail>(); const [failed, setFailed] = useState(false); const [expanded, setExpanded] = useState(false);
  useEffect(() => {
    const abort = new AbortController();
    session.request(`${webhookAdminPath}/deliveries/${deliveryId}`, { signal: abort.signal }, value => decodeDeliveryDetail(value, deliveryId))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, '投递详情加载失败'); } });
    return () => abort.abort();
  }, [deliveryId, notify]);
  const attempts = detail ? newestAttemptsFirst(detail.attempts) : [];
  const visible = expanded ? attempts : attempts.slice(0, collapsedAttempts);
  return <Drawer open size={600} rootClassName="webhook-detail" onClose={onClose} destroyOnHidden
    title={<span className="webhook-drawer-title">投递详情{detail && <Tag color={deliveryStatusColors[detail.status]}>{deliveryStatusLabels[detail.status]}</Tag>}</span>}
    footer={detail && <div className="webhook-drawer-footer"><Button onClick={onClose}>关闭</Button>
      {canManage && isReplayable(detail.status) && <Button type="primary" onClick={() => onReplay(detail)}>Replay</Button>}</div>}>
    {failed ? <Alert type="warning" showIcon title="投递详情暂时无法加载" /> : !detail ? <Spin /> : <>
      <Descriptions column={1} size="small" items={[
        { key: 'event', label: 'Event ID', children: <span className="mono">{detail.eventId}</span> },
        { key: 'type', label: '事件类型', children: `${detail.eventType} · schema ${detail.event.schemaVersion}` },
        { key: 'source', label: '来源系统', children: detail.event.sourceSystem },
        { key: 'endpoint', label: '端点', children: endpointUrl ?? attempts[0]?.requestUrl ?? detail.endpointId },
        { key: 'time', label: '创建时间', children: date(detail.createTime) },
        { key: 'attempts', label: '尝试次数', children: detail.attemptCount },
        { key: 'origin', label: '发起方式', children: detail.requestedBy ? `人工 Replay · 操作人 ${detail.requestedBy}` : '自动投递' },
        ...(detail.replayOfDeliveryId ? [{ key: 'replay', label: '重发自', children: <span className="mono">#{detail.replayOfDeliveryId}</span> }] : []),
      ]} />
      <h3 className="webhook-subtitle">事件内容（Envelope）</h3>
      <pre className="webhook-envelope">{JSON.stringify(detail.event.envelope, null, 2)}</pre>
      <h3 className="webhook-subtitle">尝试记录（最新在前）</h3>
      {attempts.length === 0 ? <div className="muted">尚未发送</div> : <div className="webhook-attempts">
        {visible.map(attempt => <AttemptRow key={attempt.attemptNo} attempt={attempt} />)}
        {attempts.length > collapsedAttempts && <Button type="link" size="small" onClick={() => setExpanded(v => !v)}>
          {expanded ? '收起' : `展开其余 ${attempts.length - collapsedAttempts} 次尝试`}</Button>}
      </div>}
    </>}
  </Drawer>;
}

/** 单次尝试：时间、结果、耗时与使用的密钥序号（宽限期内带旧密钥）。 */
function AttemptRow({ attempt }: { attempt: DeliveryAttempt }) {
  const result = attempt.status === 'SUCCEEDED' ? <span className="webhook-ok">{attempt.httpStatus === null ? '成功' : `HTTP ${attempt.httpStatus}`}</span>
    : attempt.status === 'FAILED' ? <span className="webhook-error">{failureText(attempt.failureCode, attempt.httpStatus)}</span>
    : <span>{attemptStatusLabels[attempt.status]}</span>;
  return <div className="webhook-attempt">
    <span className="muted">#{attempt.attemptNo}</span><span>{date(attempt.startedTime)}</span>{result}
    <span className="muted">{attempt.durationMs === null ? '—' : `${attempt.durationMs} ms`}</span>
    <span className="muted">密钥第 {attempt.rotationNo} 号{attempt.graceRotationNo === null ? '' : ` + 第 ${attempt.graceRotationNo} 号`}</span>
  </div>;
}
