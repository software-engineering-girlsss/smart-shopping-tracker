// src/store/index.ts
import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { CartItem, Product } from '../types';

interface CartStore {
  // Auth
  userId: string | null;
  userName: string | null;
  userEmail: string | null;
  setUser: (id: string, name: string, email?: string) => void;
  clearUser: () => void;

  // Picnic personalization
  picnicConnected: boolean;
  picnicEmail: string | null;
  zipCode: string | null;
  setPicnicConnection: (email: string, zipCode?: string | null) => void;
  clearPicnicConnection: () => void;

  // Cart
  items: CartItem[];
  addItem: (product: Product, quantity?: number, tempId?: string) => void;
  addRawCartItem: (item: CartItem) => void;
  removeItem: (itemId: string) => void;
  updateItem: (itemId: string, changes: Partial<CartItem>) => void;
  checkOff: (itemId: string) => void;
  clearCart: () => void;

  // Favorites — product IDs
  favorites: string[];
  setFavorites: (ids: string[]) => void;
  toggleFavorite: (productId: string) => void;
  isFavorite: (productId: string) => boolean;

  // Favorites — server entry IDs (product_id → favorite entry id for DELETE)
  favoriteEntryIds: Record<string, string>;
  setFavoriteEntryIds: (map: Record<string, string>) => void;
  setFavoriteEntryId: (productId: string, entryId: string) => void;
  removeFavoriteEntryId: (productId: string) => void;

  // Favorites — cached product objects for display
  favoriteProductsCache: Record<string, Product>;
  cacheFavoriteProduct: (product: Product) => void;
  evictFavoriteProduct: (productId: string) => void;

  // UI
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  activeCategory: string;
  setActiveCategory: (cat: string) => void;
}

export const useStore = create<CartStore>()(
  persist(
    (set, get) => ({
      // Auth
      userId: null,
      userName: null,
      userEmail: null,
      setUser: (id, name, email) => set(state => ({
        userId: id,
        userName: name,
        userEmail: email !== undefined ? email : state.userEmail,
      })),
      clearUser: () => set({ userId: null, userName: null, userEmail: null }),

      // Picnic personalization
      picnicConnected: false,
      picnicEmail: null,
      zipCode: null,
      setPicnicConnection: (email, zipCode) => set({ picnicConnected: true, picnicEmail: email, zipCode: zipCode ?? null }),
      clearPicnicConnection: () => set({ picnicConnected: false, picnicEmail: null, zipCode: null }),

      // Cart
      items: [],

      addItem: (product, quantity = 1, tempId) => {
        const existing = get().items.find(i => i.product_id === product.id);
        if (existing) {
          set(state => ({
            items: state.items.map(i =>
              i.product_id === product.id
                ? { ...i, quantity: i.quantity + quantity }
                : i
            ),
          }));
        } else {
          set(state => ({
            items: [
              ...state.items,
              {
                id: tempId ?? Math.random().toString(36).slice(2),
                product_id: product.id,
                name: product.name,
                image_url: product.image_url,
                quantity,
                unit: product.prices[0]?.unit ?? '1x',
                checked_off: false,
                store_prices: product.prices,
              },
            ],
          }));
        }
      },

      addRawCartItem: (item) =>
        set(state => ({
          items: state.items.some(i => i.id === item.id || i.name === item.name)
            ? state.items
            : [...state.items, item],
        })),

      removeItem: (itemId) =>
        set(state => ({ items: state.items.filter(i => i.id !== itemId) })),

      updateItem: (itemId, changes) =>
        set(state => ({
          items: state.items.map(i => (i.id === itemId ? { ...i, ...changes } : i)),
        })),

      checkOff: (itemId) =>
        set(state => ({
          items: state.items.map(i =>
            i.id === itemId ? { ...i, checked_off: !i.checked_off } : i
          ),
        })),

      clearCart: () => set({ items: [] }),

      // Favorites
      favorites: [],
      setFavorites: (ids) => set({ favorites: ids }),
      toggleFavorite: (productId) =>
        set(state => ({
          favorites: state.favorites.includes(productId)
            ? state.favorites.filter(id => id !== productId)
            : [...state.favorites, productId],
        })),
      isFavorite: (productId) => get().favorites.includes(productId),

      // Favorite entry IDs (product_id → server entry id)
      favoriteEntryIds: {},
      setFavoriteEntryIds: (map) => set({ favoriteEntryIds: map }),
      setFavoriteEntryId: (productId, entryId) =>
        set(state => ({ favoriteEntryIds: { ...state.favoriteEntryIds, [productId]: entryId } })),
      removeFavoriteEntryId: (productId) =>
        set(state => {
          const next = { ...state.favoriteEntryIds };
          delete next[productId];
          return { favoriteEntryIds: next };
        }),

      // Favorite products cache
      favoriteProductsCache: {},
      cacheFavoriteProduct: (product) =>
        set(state => ({ favoriteProductsCache: { ...state.favoriteProductsCache, [product.id]: product } })),
      evictFavoriteProduct: (productId) =>
        set(state => {
          const next = { ...state.favoriteProductsCache };
          delete next[productId];
          return { favoriteProductsCache: next };
        }),

      // UI
      searchQuery: '',
      setSearchQuery: (q) => set({ searchQuery: q }),
      activeCategory: 'all',
      setActiveCategory: (cat) => set({ activeCategory: cat }),
    }),
    {
      name: 'frisch-store',
      storage: createJSONStorage(() => AsyncStorage),
      // Don't persist the product cache — it's rebuilt on each session via refresh().
      partialize: (state) => {
        const { favoriteProductsCache: _cache, ...rest } = state;
        return rest;
      },
    }
  )
);
