import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Spin, Tag } from 'antd';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  decodeDeliveryDetail, deliveryStatusColors, failureText, isReplayable, newestAttemptsFirst,
  webhookAdminPath,
} from './webhook';
import type { DeliveryAttempt, DeliveryDetail, DeliverySummary } from './webhook';

/** 默认展示的最近尝试条数，其余可展开。 */
const collapsedAttempts = 3;

/** 投递详情抽屉：基本信息、冻结的 Envelope、尝试记录（最新在前）；有维护权限且已结束时可 Replay。 */
export function DeliveryDetailDrawer({ deliveryId, endpointUrl, canManage, onReplay, onClose }: {
  deliveryId: string; endpointUrl?: string; canManage: boolean; onReplay: (delivery: DeliverySummary) => void; onClose: () => void;
}) {
  const notify = useErrorNotification(); const t = useT('webhook');
  const [detail, setDetail] = useState<DeliveryDetail>(); const [failed, setFailed] = useState(false); const [expanded, setExpanded] = useState(false);
  useEffect(() => {
    const abort = new AbortController();
    session.request(`${webhookAdminPath}/deliveries/${deliveryId}`, { signal: abort.signal }, value => decodeDeliveryDetail(value, deliveryId))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, t('detail.loadFailed')); } });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [deliveryId, notify]);
  const attempts = detail ? newestAttemptsFirst(detail.attempts) : [];
  const visible = expanded ? attempts : attempts.slice(0, collapsedAttempts);
  return <Drawer open size={600} rootClassName="webhook-detail" onClose={onClose} destroyOnHidden
    title={<span className="webhook-drawer-title">{t('detail.title')}{detail && <Tag color={deliveryStatusColors[detail.status]}>{enumLabel(t, 'enum.deliveryStatus', detail.status)}</Tag>}</span>}
    footer={detail && <div className="webhook-drawer-footer"><Button onClick={onClose}>{t('detail.close')}</Button>
      {canManage && isReplayable(detail.status) && <Button type="primary" onClick={() => onReplay(detail)}>{t('deliveries.replay')}</Button>}</div>}>
    {failed ? <Alert type="warning" showIcon title={t('detail.unavailable')} /> : !detail ? <Spin /> : <>
      <Descriptions column={1} size="small" items={[
        { key: 'event', label: t('deliveries.field.eventId'), children: <span className="mono">{detail.eventId}</span> },
        { key: 'type', label: t('deliveries.field.eventType'), children: t('detail.eventTypeValue', { eventType: detail.eventType, schemaVersion: detail.event.schemaVersion }) },
        { key: 'source', label: t('detail.sourceSystem'), children: detail.event.sourceSystem },
        { key: 'endpoint', label: t('deliveries.field.endpoint'), children: endpointUrl ?? attempts[0]?.requestUrl ?? detail.endpointId },
        { key: 'time', label: t('deliveries.field.createdAt'), children: formatDateTime(detail.createTime) },
        { key: 'attempts', label: t('detail.attemptCount'), children: detail.attemptCount },
        { key: 'origin', label: t('detail.origin'), children: detail.requestedBy ? t('detail.manualReplay', { actor: detail.requestedBy }) : t('detail.automatic') },
        ...(detail.replayOfDeliveryId ? [{ key: 'replay', label: t('detail.replayOf'), children: <span className="mono">#{detail.replayOfDeliveryId}</span> }] : []),
      ]} />
      <h3 className="webhook-subtitle">{t('detail.envelope')}</h3>
      <pre className="webhook-envelope">{JSON.stringify(detail.event.envelope, null, 2)}</pre>
      <h3 className="webhook-subtitle">{t('detail.attempts')}</h3>
      {attempts.length === 0 ? <div className="muted">{t('detail.notSent')}</div> : <div className="webhook-attempts">
        {visible.map(attempt => <AttemptRow key={attempt.attemptNo} attempt={attempt} />)}
        {attempts.length > collapsedAttempts && <Button type="link" size="small" onClick={() => setExpanded(v => !v)}>
          {expanded ? t('detail.collapse') : t('detail.expand', { count: attempts.length - collapsedAttempts })}</Button>}
      </div>}
    </>}
  </Drawer>;
}

/** 单次尝试：时间、结果、耗时与使用的密钥序号（宽限期内带旧密钥）。 */
function AttemptRow({ attempt }: { attempt: DeliveryAttempt }) {
  const t = useT('webhook');
  const result = attempt.status === 'SUCCEEDED' ? <span className="webhook-ok">{attempt.httpStatus === null ? enumLabel(t, 'enum.attemptStatus', 'SUCCEEDED') : `HTTP ${attempt.httpStatus}`}</span>
    : attempt.status === 'FAILED' ? <span className="webhook-error">{failureText(attempt.failureCode, attempt.httpStatus)}</span>
    : <span>{enumLabel(t, 'enum.attemptStatus', attempt.status)}</span>;
  return <div className="webhook-attempt">
    <span className="muted">#{attempt.attemptNo}</span><span>{formatDateTime(attempt.startedTime)}</span>{result}
    <span className="muted">{attempt.durationMs === null ? '—' : `${attempt.durationMs} ms`}</span>
    <span className="muted">{attempt.graceRotationNo === null ? t('detail.attemptSecret', { rotationNo: attempt.rotationNo })
      : t('detail.attemptSecretWithGrace', { rotationNo: attempt.rotationNo, graceRotationNo: attempt.graceRotationNo })}</span>
  </div>;
}
