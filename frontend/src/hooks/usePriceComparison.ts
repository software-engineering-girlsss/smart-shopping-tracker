import { useEffect, useRef, useState } from 'react';
import { cartApi } from '../api/client';
import type { CartPriceComparison } from '../types';

const DEBOUNCE_MS = 700;

export function usePriceComparison(userId: string | null, totalQuantity: number, itemIds: string = '') {
  const [comparison, setComparison] = useState<CartPriceComparison | null>(null);
  const [loading, setLoading] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!totalQuantity || !userId) { setComparison(null); return; }

    if (timerRef.current) clearTimeout(timerRef.current);
    setLoading(true);

    timerRef.current = setTimeout(() => {
      cartApi.priceComparison()
        .then((data) => setComparison(data as CartPriceComparison))
        .catch((err) => { console.warn('price comparison failed', err); setComparison(null); })
        .finally(() => setLoading(false));
    }, DEBOUNCE_MS);

    return () => { if (timerRef.current) clearTimeout(timerRef.current); };
  }, [userId, totalQuantity, itemIds]);

  return { comparison, loading };
}
