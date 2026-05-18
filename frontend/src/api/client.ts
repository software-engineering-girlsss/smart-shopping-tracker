// src/api/client.ts
import AsyncStorage from '@react-native-async-storage/async-storage';

export const BASE_URL =
  process.env.EXPO_PUBLIC_API_URL ?? 'https://api.baskt.me/api/v2';

const BASE_URL_V1 = BASE_URL.replace(/\/api\/v2$/, '/api/v1');

export class ApiError extends Error {
  status: number;
  body?: unknown;
  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler | null = null;
export function setUnauthorizedHandler(fn: UnauthorizedHandler | null) {
  onUnauthorized = fn;
}

let isRefreshing = false;
let refreshPromise: Promise<string | null> | null = null;

async function tryRefreshToken(): Promise<string | null> {
  if (isRefreshing) return refreshPromise;
  isRefreshing = true;
  refreshPromise = (async () => {
    try {
      const refreshToken = await AsyncStorage.getItem('refresh_token');
      if (!refreshToken) return null;
      const res = await fetch(`${BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refresh_token: refreshToken }),
      });
      if (!res.ok) return null;
      const data = await res.json();
      if (!data.access_token) return null;
      await AsyncStorage.setItem('auth_token', data.access_token);
      if (data.refresh_token) await AsyncStorage.setItem('refresh_token', data.refresh_token);
      return data.access_token as string;
    } catch {
      return null;
    } finally {
      isRefreshing = false;
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

async function request<T>(path: string, options?: RequestInit & { noAuth?: boolean }, _isRetry = false, _base = BASE_URL): Promise<T> {
  const { noAuth, ...fetchOptions } = options ?? {};
  const token = noAuth ? null : await AsyncStorage.getItem('auth_token');
  const res = await fetch(`${_base}${path}`, {
    ...fetchOptions,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(fetchOptions.headers ?? {}),
    },
  });

  let body: unknown = null;
  const text = await res.text();
  if (text) {
    try { body = JSON.parse(text); } catch { body = text; }
  }

  if (res.status === 401 && !_isRetry) {
    const newToken = await tryRefreshToken();
    if (newToken) return request<T>(path, options, true, _base);
    try { onUnauthorized?.(); } catch { /* ignore */ }
  }

  if (!res.ok) {
    const msg =
      (body && typeof body === 'object' && 'message' in body && typeof (body as any).message === 'string')
        ? (body as any).message
        : (body && typeof body === 'object' && 'error' in body && typeof (body as any).error === 'string')
          ? (body as any).error
          : `API ${res.status} ${res.statusText}`;
    throw new ApiError(res.status, msg, body);
  }
  return body as T;
}

// --- Auth ---
export interface ConnectedAccount {
  provider: string;
  email: string;
  connected_at: string;
  expires_at?: number;
  zip_code?: string;
}

export interface AuthUser {
  id: string | number;
  email: string;
  name: string;
  role?: string;
  connected_accounts?: ConnectedAccount[];
}
export interface AuthResponse {
  user: AuthUser;
  access_token: string;
  refresh_token?: string;
}

export interface PendingVerificationResponse {
  pending_verification: true;
  email: string;
}

export const authApi = {
  login: (email: string, password: string) =>
    request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      noAuth: true,
    }),
  register: (name: string, email: string, password: string) =>
    request<AuthResponse | PendingVerificationResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ name, email, password }),
      noAuth: true,
    }),
  verifyEmail: (email: string, code: string) =>
    request<AuthResponse>('/auth/verify-email', {
      method: 'POST',
      body: JSON.stringify({ email, code }),
      noAuth: true,
    }),
  resendCode: (email: string) =>
    request<{ message: string }>('/auth/resend-code', {
      method: 'POST',
      body: JSON.stringify({ email }),
      noAuth: true,
    }),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  refresh: (refresh_token: string) =>
    request<AuthResponse>('/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refresh_token }),
    }),
  me: (signal?: AbortSignal) => request<AuthUser>('/users/me', signal ? { signal } : undefined),
  updateProfile: (name: string) =>
    request<AuthUser>('/users/me', {
      method: 'PATCH',
      body: JSON.stringify({ name }),
    }),
  changePassword: (newPassword: string) =>
    request<{ message: string }>('/auth/change-password', {
      method: 'PUT',
      body: JSON.stringify({ new_password: newPassword }),
    }, false, BASE_URL_V1),
  forgotPassword: (email: string) =>
    request<{ message: string }>('/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
      noAuth: true,
    }),
  resetPassword: (email: string, code: string, newPassword: string) =>
    request<{ message: string }>('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ email, code, new_password: newPassword }),
      noAuth: true,
    }),
};

// --- Products ---

// Used for replacing store brand names in display names
const STORE_BRAND_REPLACE =
  /\b(REWE\s*(Beste\s+Wahl|Feine\s+Welt|Ready\s+to\s+eat|to\s+go)?|Beste\s+Wahl|K-Classic|Kaufland|Picnic|Ja!|EDEKA|Aldi\s*(Süd|Nord)?|Lidl|Netto|Penny)\b\s*/gi;

// Used for detecting store branding before normalization (no `g` flag — safe for repeated .test())
const STORE_BRAND_CHECK =
  /\b(REWE|Beste\s+Wahl|K-Classic|Kaufland|Picnic|Ja!|EDEKA|Aldi|Lidl|Netto|Penny)\b/i;

const STUB_SUFFIX = /\s*\[stub\]/gi;

const PROXY_CDN = /^https:\/\/(storefront-prod\.[a-z0-9-]+\.picnicinternational\.com|img\.rewe-static\.de|images\.rewe\.de)\//;

export function proxyImageUrl(url: string): string {
  if (!url || !PROXY_CDN.test(url)) return url;
  return `${BASE_URL}/images/proxy?url=${encodeURIComponent(url)}`;
}

function normalizeProductName(name: string): string {
  return name
    .replace(STUB_SUFFIX, '')
    .replace(STORE_BRAND_REPLACE, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

/**
 * Priority for home screen ordering:
 *   3 — available in both stores
 *   2 — single store, but generic name (no store branding)
 *   1 — single store, branded name
 */
function productPriority(raw: any): 1 | 2 | 3 {
  const storeCount = Array.isArray(raw.prices) ? raw.prices.length : 0;
  if (storeCount >= 2) return 3;
  return STORE_BRAND_CHECK.test(raw.name ?? '') ? 1 : 2;
}

function enrichProducts(raw: any[]): any[] {
  return raw.map(p => ({
    ...p,
    _priority: productPriority(p),
    name: normalizeProductName(p.name ?? ''),
    normalized_name: normalizeProductName(p.normalized_name ?? p.name ?? ''),
    image_url: proxyImageUrl(p.image_url ?? ''),
  }));
}

const GENERIC_FALLBACK_QUERIES = ['Milch', 'Butter', 'Eier', 'Brot', 'Käse', 'Joghurt'];
const MIN_PRODUCTS = 6;

export const productsApi = {
  search: (q: string, page = 1, limit = 20, sort = 'relevance') => {
    const params = new URLSearchParams({ q, page: String(page), limit: String(limit), sort });
    return request<{ items: any[]; total?: number; page?: number }>(`/products?${params}`)
      .then(r => ({ ...r, items: r.items.map(p => ({ ...p, image_url: proxyImageUrl(p.image_url ?? '') })) }));
  },

  featured: () => request<any[]>('/products/featured'),

  genericFeatured: async (): Promise<any[]> => {
    // Try featured endpoint first
    let enriched: any[] = [];
    try {
      const raw = await request<any[]>('/products/featured');
      const arr = Array.isArray(raw) ? raw : (raw as any)?.items ?? [];
      enriched = enrichProducts(arr);
    } catch { /* ignore — try supplementary searches */ }

    if (enriched.length >= MIN_PRODUCTS) return enriched;

    // Supplement with category searches if backend returned fewer than MIN_PRODUCTS items
    const settled = await Promise.allSettled(
      GENERIC_FALLBACK_QUERIES.map(q =>
        request<{ items: any[] }>(`/products?q=${encodeURIComponent(q)}&limit=5&sort=relevance`)
          .then(r => r.items ?? [])
      )
    );

    const extraRaw = settled
      .filter((r): r is PromiseFulfilledResult<any[]> => r.status === 'fulfilled')
      .flatMap(r => r.value);

    const seen = new Set(enriched.map((p: any) => p.id));
    const combined = [...enriched];
    for (const p of enrichProducts(extraRaw)) {
      if (!seen.has(p.id)) { seen.add(p.id); combined.push(p); }
    }
    return combined;
  },

  getById: (id: string) => request<any>(`/products/${id}`),
};

// --- Cart (user-scoped; auth header identifies user) ---
export const cartApi = {
  get: () => request<any>('/cart'),
  addItem: (product_id: string, quantity = 1) =>
    request<any>('/cart/items', {
      method: 'POST',
      body: JSON.stringify({ product_id, quantity }),
    }),
  addQuery: (query: string, quantity = 1) =>
    request<any>('/cart/items', {
      method: 'POST',
      body: JSON.stringify({ query, quantity }),
    }),
  updateItem: (itemId: string, data: {
    quantity?: number;
    filters?: any;
    store_selection?: { store: string; product_id?: string; name: string; image_url?: string; price?: number };
  }) =>
    request<any>(`/cart/items/${itemId}`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    }),
  removeItem: (itemId: string) =>
    request<void>(`/cart/items/${itemId}`, { method: 'DELETE' }),
  clear: () => request<void>('/cart', { method: 'DELETE' }),
  priceComparison: () => request<any>('/cart/comparison'),
};

// --- Favorites ---
export const favoritesApi = {
  list: (type?: 'specific' | 'generic') =>
    request<any[]>(`/favorites${type ? `?type=${type}` : ''}`),
  add: (product_id: string) =>
    request<any>('/favorites', {
      method: 'POST',
      body: JSON.stringify({ type: 'specific', product_id }),
    }),
  remove: (favoriteId: string) =>
    request<void>(`/favorites/${favoriteId}`, { method: 'DELETE' }),
};

// --- Receipts ---
export const receiptsApi = {
  scan: async (imageUri: string) => {
    const formData = new FormData();
    formData.append('image', {
      uri: imageUri,
      type: 'image/jpeg',
      name: 'receipt.jpg',
    } as unknown as Blob);
    const token = await AsyncStorage.getItem('auth_token');
    return fetch(`${BASE_URL}/receipts/scan`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    }).then((r) => r.json());
  },
};

// --- User personalization ---
export interface PicnicNeeds2FAResponse {
  needs_2fa: true;
  email: string;
  message: string;
}

export const userApi = {
  connectPicnic: (picnicEmail: string, picnicPassword: string, zipCode?: string) =>
    request<ConnectedAccount | PicnicNeeds2FAResponse>('/users/me/accounts/picnic', {
      method: 'POST',
      body: JSON.stringify({ email: picnicEmail, password: picnicPassword, zip_code: zipCode || undefined }),
    }),
  verify2fa: (otp: string) =>
    request<ConnectedAccount>('/users/me/accounts/picnic/2fa-verify', {
      method: 'POST',
      body: JSON.stringify({ otp }),
    }),
  disconnectPicnic: () =>
    request<void>('/users/me/accounts/picnic', { method: 'DELETE' }),
};

// --- Categories ---
export const categoriesApi = {
  list: () => request<any[]>('/categories', { noAuth: true }),
  getProducts: (slug: string, limit = 50) =>
    request<any[]>(`/categories/${encodeURIComponent(slug)}/products?limit=${limit}`, { noAuth: true })
      .then(items => items.map(p => ({ ...p, image_url: proxyImageUrl(p.image_url ?? '') }))),
};

// --- Health ---
export const healthApi = {
  check: () => request<{ status: string; version: string; stores: string[] }>('/health'),
};
