import { AppstoreOutlined } from '@ant-design/icons';
import './referenceData.css';

/** 国旗：Platform 给出公开地址时显示图片，否则灰色占位。 */
export function Flag({ url, label }: { url: string | null; label: string }) {
  return url ? <img className="refdata-flag" src={url} alt={label} loading="lazy" /> : <span className="refdata-flag is-empty" aria-label={label} />;
}

/** 行业或大类图标：Platform 公开地址，否则通用图标。 */
export function IndustryIcon({ url }: { url: string | null }) {
  return url ? <img className="refdata-icon" src={url} alt="" loading="lazy" /> : <span className="refdata-icon is-fallback"><AppstoreOutlined /></span>;
}
