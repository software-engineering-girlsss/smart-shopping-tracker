// src/types/index.ts

export interface Category {
  id: number;
  name: string;
  name_en: string;
  slug: string;
  icon?: string | null;
  sort_order: number;
  children?: Category[];
}

export type StoreId =
  | 'netto'
  | 'kaufland'
  | 'rewe'
  | 'rewe_online'
  | 'picnic';

export interface StorePrice {
  store: StoreId;
  price: number;
  unit: string;
  available: boolean;
}

export interface Store {
  id: StoreId;
  name: string;
  type: 'offline' | 'online';
  color: string;
}

export interface Promotion {
  id: string;
  product_id: string;
  store: StoreId;
  discount_percent: number;
  original_price: number;
  promo_price: number;
  valid_until: string;
  badge: string;
}

export interface Product {
  id: string;
  name: string;
  normalized_name: string;
  brand: string;
  image_url: string;
  category: string;
  prices: StorePrice[];
  best_price: { store: StoreId; price: number };
  score?: number;
  is_favorite?: boolean;
  promotion?: Promotion;
}

export interface StoreSelection {
  product_id?: string;
  name: string;
  image_url?: string;
  price?: number;
}

export interface CartItem {
  id: string;
  product_id: string;
  name: string;
  image_url: string;
  quantity: number;
  unit: string;
  checked_off: boolean;
  store_prices?: StorePrice[];
  store_selections?: Record<string, StoreSelection>;
}

export interface Cart {
  cart_id: string;
  items: CartItem[];
  updated_at: string;
}

export interface StoreCartTotal {
  store: StoreId;
  name: string;
  type: 'offline' | 'online';
  total: number;
  available_items: number;
  missing_items: number;
  deeplink?: string;
  color: string;
}

export interface CartPriceComparison {
  cart_id: string;
  stores: StoreCartTotal[];
  savings_vs_most_expensive: number;
}

export interface Recipe {
  id: string;
  name: string;
  image_url: string;
  duration_min: number;
  servings: number;
  category: string;
  ingredients: RecipeIngredient[];
}

export interface RecipeIngredient {
  product_id: string;
  name: string;
  amount: number;
  unit: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  preferred_stores: StoreId[];
  dietary: string[];
}

export interface ConnectedAccount {
  provider: string;
  email: string;
  connected_at: string;
  expires_at?: number;
  zip_code?: string;
}

