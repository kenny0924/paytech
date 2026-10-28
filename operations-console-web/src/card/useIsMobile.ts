import { useEffect, useState } from 'react';

const query = '(max-width: 640px)';

/** 窄屏（≤ 640px，画板 08 的 390 宽度）：号段改卡片列表、筛选收进底部抽屉。 */
export function useIsMobile(): boolean {
  const [mobile, setMobile] = useState(() => typeof window !== 'undefined' && window.matchMedia(query).matches);
  useEffect(() => {
    const media = window.matchMedia(query);
    const sync = () => setMobile(media.matches);
    sync();
    media.addEventListener('change', sync);
    return () => media.removeEventListener('change', sync);
  }, []);
  return mobile;
}
