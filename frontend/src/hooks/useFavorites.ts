import { useCallback, useEffect, useState } from 'react';
import { favoritesApi, productsApi } from '../api/client';
import { useStore } from '../store';
import type { Product } from '../types';

interface FavoriteEntry {
  id: string;
  type: 'specific' | 'generic';
  product_id?: string | null;
}

export function useFavorites() {
  const userId = useStore(s => s.userId);
  const favorites = useStore(s => s.favorites);
  const [loading, setLoading] = useState(false);
  const setFavorites = useStore(s => s.setFavorites);
  const favoriteEntryIds = useStore(s => s.favoriteEntryIds);
  const setFavoriteEntryIds = useStore(s => s.setFavoriteEntryIds);
  const setFavoriteEntryId = useStore(s => s.setFavoriteEntryId);
  const removeFavoriteEntryId = useStore(s => s.removeFavoriteEntryId);
  const cacheFavoriteProduct = useStore(s => s.cacheFavoriteProduct);
  const evictFavoriteProduct = useStore(s => s.evictFavoriteProduct);

  // Fetch favorites from server, populate store + product cache.
  const refresh = useCallback(async () => {
    if (!userId) return;
    setLoading(true);
    try {
      const favs = (await favoritesApi.list()) as FavoriteEntry[];
      const entryMap: Record<string, string> = {};
      const ids: string[] = [];

      for (const f of favs) {
        if (f.type === 'specific' && f.product_id) {
          entryMap[f.product_id] = f.id;
          ids.push(f.product_id);
        }
      }

      setFavoriteEntryIds(entryMap);
      setFavorites(ids);

      // Populate product cache for each favorite; stop loading once all settle.
      await Promise.allSettled(
        ids.map(productId =>
          productsApi.getById(productId)
            .then(p => { if (p) cacheFavoriteProduct(p as Product); })
            .catch(() => {})
        )
      );
    } catch (err) {
      console.warn('Failed to load favorites from server', err);
    } finally {
      setLoading(false);
    }
  }, [userId, setFavorites, setFavoriteEntryIds, cacheFavoriteProduct]);

  useEffect(() => { refresh(); }, [refresh]);

  // Toggle: accepts full Product so we can cache it for display.
  const toggle = useCallback(async (product: Product) => {
    const productId = product.id;
    const currentlyFav = favorites.includes(productId);

    // Optimistic local update.
    setFavorites(
      currentlyFav
        ? favorites.filter(id => id !== productId)
        : [...favorites, productId]
    );

    if (!userId) return;

    try {
      if (currentlyFav) {
        evictFavoriteProduct(productId);
        const favId = favoriteEntryIds[productId];
        if (favId) {
          await favoritesApi.remove(favId);
          removeFavoriteEntryId(productId);
        }
      } else {
        cacheFavoriteProduct(product);
        const created = (await favoritesApi.add(productId)) as FavoriteEntry;
        if (created?.id) {
          setFavoriteEntryId(productId, created.id);
        }
      }
    } catch (err) {
      console.warn('Favorite sync failed', err);
      // Revert optimistic update — restore both favorites list AND product cache.
      if (currentlyFav) {
        setFavorites([...favorites, productId]);
        cacheFavoriteProduct(product); // re-cache so UI doesn't get stuck in loading state
      } else {
        setFavorites(favorites.filter(id => id !== productId));
        evictFavoriteProduct(productId);
      }
    }
  }, [
    userId,
    favorites,
    favoriteEntryIds,
    setFavorites,
    setFavoriteEntryId,
    removeFavoriteEntryId,
    cacheFavoriteProduct,
    evictFavoriteProduct,
  ]);

  const isFavorite = useCallback((id: string) => favorites.includes(id), [favorites]);

  return { favorites, toggle, isFavorite, refresh, loading };
}
