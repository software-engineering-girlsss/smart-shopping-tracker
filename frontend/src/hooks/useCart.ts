import { useEffect } from 'react';
import { useStore } from '../store';
import { cartApi } from '../api/client';
import type { Product } from '../types';

export function useCart() {
  const { userId, items, addItem, addRawCartItem, removeItem, updateItem, checkOff, clearCart: clearLocal } = useStore();

  // Load server cart on mount: sync IDs for existing items, add missing ones.
  useEffect(() => {
    if (!userId) return;
    cartApi.get().then((cart: any) => {
      if (!Array.isArray(cart?.items)) return;

      // Group server items by name to merge duplicate query entries.
      const grouped = new Map<string, { id: string; quantity: number; image_url: string; unit: string; product_id: string }>();
      for (const si of cart.items) {
        const name: string = si.query ?? si.name ?? '';
        const serverId = String(si.id);
        const qty: number = si.quantity ?? 1;
        const existing = grouped.get(name);
        if (existing) {
          existing.quantity += qty;
        } else {
          grouped.set(name, {
            id: serverId,
            quantity: qty,
            image_url: si.image_url ?? '',
            unit: si.unit ?? '1x',
            product_id: si.product_id ? String(si.product_id) : serverId,
          });
        }
      }

      const snapshot = useStore.getState().items;
      for (const [name, si] of grouped) {
        const local = snapshot.find(i => i.name === name || i.id === si.id);
        if (local) {
          if (local.id !== si.id) updateItem(local.id, { id: si.id });
        } else {
          addRawCartItem({
            id: si.id,
            product_id: si.product_id,
            name,
            image_url: si.image_url,
            quantity: si.quantity,
            unit: si.unit,
            checked_off: false,
          });
        }
      }
    }).catch(() => { /* ignore — offline or not authenticated */ });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  const addToCart = async (product: Product, quantity = 1) => {
    const existing = items.find(i => i.product_id === product.id);
    const tempId = existing?.id ?? Math.random().toString(36).slice(2);

    addItem(product, quantity, tempId);

    if (!userId) return;
    try {
      const result: any = await cartApi.addQuery(product.name, quantity);
      // Sync server-assigned ID into local state so remove/update calls work.
      if (result?.id && !existing) {
        updateItem(tempId, { id: result.id });
      }
    } catch (err) {
      console.warn('Failed to sync cart item to server', err);
      if (!existing) removeItem(tempId);
    }
  };

  const removeFromCart = async (itemId: string) => {
    removeItem(itemId);
    if (!userId) return;
    try {
      await cartApi.removeItem(itemId);
    } catch (err) {
      console.warn('Failed to remove cart item on server', err);
    }
  };

  const decreaseFromCart = async (product: Product) => {
    const existing = items.find(i => i.product_id === product.id);
    if (!existing) return;
    if (existing.quantity > 1) {
      const newQty = existing.quantity - 1;
      updateItem(existing.id, { quantity: newQty });
      if (userId) cartApi.updateItem(existing.id, { quantity: newQty }).catch(() => {});
    } else {
      await removeFromCart(existing.id);
    }
  };

  const markBought = async (itemId: string) => {
    checkOff(itemId);
  };

  const addGenericItem = async (name: string, quantity = 1) => {
    const trimmed = name.trim();
    if (!trimmed) return;
    const existing = items.find(i => i.name.toLowerCase() === trimmed.toLowerCase());
    if (existing) {
      updateItem(existing.id, { quantity: existing.quantity + quantity });
      if (userId) cartApi.updateItem(existing.id, { quantity: existing.quantity + quantity }).catch(() => {});
      return;
    }
    const tempId = Math.random().toString(36).slice(2);
    addRawCartItem({ id: tempId, product_id: tempId, name: trimmed, image_url: '', quantity, unit: '1x', checked_off: false });
    if (!userId) return;
    try {
      const result: any = await cartApi.addQuery(trimmed, quantity);
      if (result?.id) updateItem(tempId, { id: result.id });
    } catch {
      removeItem(tempId);
    }
  };

  const clearCart = async () => {
    clearLocal();
    if (!userId) return;
    try {
      await cartApi.clear();
    } catch (err) {
      console.warn('Failed to clear cart on server', err);
    }
  };

  const totalItems = items.reduce((sum, i) => sum + i.quantity, 0);
  const uncheckedItems = items.filter(i => !i.checked_off);
  const checkedItems = items.filter(i => i.checked_off);

  return { items, totalItems, uncheckedItems, checkedItems, addToCart, addGenericItem, removeFromCart, decreaseFromCart, markBought, clearCart };
}
