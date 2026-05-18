import { useState, useEffect, useCallback } from 'react';
import { productsApi } from '../api/client';
import type { Product } from '../types';

export function useSearch() {
  const [query, setQuery] = useState('');
  const [committedQuery, setCommittedQuery] = useState('');
  const [results, setResults] = useState<Product[]>([]);
  const [loading, setLoading] = useState(false);

  const triggerSearch = useCallback(() => {
    setCommittedQuery(query);
  }, [query]);

  const clearSearch = useCallback(() => {
    setQuery('');
    setCommittedQuery('');
    setResults([]);
  }, []);

  useEffect(() => {
    if (!committedQuery.trim()) { setResults([]); return; }
    setLoading(true);
    productsApi.search(committedQuery)
      .then(data => setResults((data as { items: Product[] }).items ?? []))
      .catch(() => setResults([]))
      .finally(() => setLoading(false));
  }, [committedQuery]);

  return { query, setQuery, committedQuery, results, loading, triggerSearch, clearSearch };
}
