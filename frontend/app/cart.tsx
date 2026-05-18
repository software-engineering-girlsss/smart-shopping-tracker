// app/cart.tsx
import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { cartApi, productsApi, proxyImageUrl } from '../src/api/client';
import { useCart, usePriceComparison } from '../src/hooks';
import { useStore } from '../src/store';
import { colors, fonts, radius, shadow, STORE_COLORS, STORE_NAMES } from '../src/theme';
import type { CartItem, StorePrice, StoreSelection } from '../src/types';

const GREEN = '#16A34A';
const GREEN_LIGHT = '#F0FDF4';

const STORE_LOGOS: Partial<Record<string, any>> = {
  rewe:   require('../assets/images/stores/rewe.png'),
  picnic: require('../assets/images/stores/picnic.png'),
};

// ─── helpers ──────────────────────────────────────────────────────────────────

// 'rewe' and 'rewe_online' are the same physical store — backend uses 'rewe_online'
// in product prices but the UI section is keyed as 'rewe'.
function reweNorm(s: string): string {
  return s === 'rewe_online' ? 'rewe' : s;
}

function itemPriceForStore(item: CartItem, sid: string): { price: number } | undefined {
  const prices = item.store_prices;
  if (!prices || !prices.length) return undefined;
  const norm = reweNorm(sid);
  const entry = prices.find(p => reweNorm(p.store) === norm);
  return entry ? { price: entry.price } : undefined;
}

function computeStoreTotal(items: CartItem[], sid: string): number {
  return items.reduce((sum, item) => {
    const sp = itemPriceForStore(item, sid);
    return sum + (sp ? sp.price * item.quantity : 0);
  }, 0);
}

// ─── AltCard ─────────────────────────────────────────────────────────────────

function AltCard({ alt, isSelected, onPress }: { alt: AltProduct; isSelected: boolean; onPress: () => void }) {
  const [imgError, setImgError] = useState(false);
  const imgUri = alt.image_url ? proxyImageUrl(alt.image_url) : '';
  const showImage = !!imgUri && !imgError;

  return (
    <TouchableOpacity
      style={[styles.altCard, isSelected && styles.altCardSelected]}
      onPress={onPress}
      activeOpacity={0.75}
    >
      {showImage ? (
        <Image
          source={{ uri: imgUri }}
          style={{ width: 100, height: 100 }}
          contentFit="contain"
          cachePolicy="memory-disk"
          onError={() => setImgError(true)}
        />
      ) : (
        <View style={{ width: 100, height: 100, alignItems: 'center', justifyContent: 'center' }}>
          <Ionicons name="cart-outline" size={40} color={colors.text2} />
        </View>
      )}
      <Text style={styles.altCardName} numberOfLines={2}>{alt.name}</Text>
      <Text style={styles.altCardPrice}>€{alt.price.toFixed(2)}</Text>
      {isSelected && <Text style={styles.altCardSelectedBadge}>Selected</Text>}
    </TouchableOpacity>
  );
}

// ─── AlternativesPicker ──────────────────────────────────────────────────────

type AltProduct = {
  id: string;
  name: string;
  image_url: string;
  price: number;
  store_prices: StorePrice[];
};

interface AlternativesPickerProps {
  itemId: string;
  quantity: number;
  itemName: string;
  sid: string;
  currentSelection?: StoreSelection;
  onRemoveFromCart: () => void;
  onSelectAlt: (itemId: string, alt: AltProduct) => void;
}

function AlternativesPicker({ itemId, quantity: _quantity, itemName, sid, currentSelection, onRemoveFromCart, onSelectAlt }: AlternativesPickerProps) {
  const [alts, setAlts] = useState<AltProduct[] | 'loading'>('loading');

  useEffect(() => {
    let cancelled = false;
    // Strip size annotations ("500g", "1l", "4x115g", "3,5%") and skip the first
    // word (usually a brand name) so the query finds generic cross-store alternatives
    // instead of the same branded product that is already unavailable in this store.
    const stripped = itemName
      .replace(/\b\d+[,.]?\d*\s*x?\s*\d*[,.]?\d*\s*(g|kg|ml|l|cl|stk?\.?)\b/gi, '')
      .replace(/\b\d+[,.]?\d*\s*%/gi, '')
      .replace(/\s{2,}/g, ' ')
      .trim();
    const words = stripped.split(/\s+/).filter(Boolean);
    const searchQuery = words.length > 1 ? words.slice(1, 3).join(' ') : (words[0] ?? itemName);
    productsApi.search(searchQuery, 1, 12)
      .then(data => {
        if (cancelled) return;
        const results: AltProduct[] = [];
        for (const p of data.items ?? []) {
          const pr = (p.prices ?? []).find((x: any) => reweNorm(x.store) === reweNorm(sid));
          if (pr) {
            results.push({
              id: String(p.id ?? ''),
              name: p.name ?? itemName,
              image_url: p.image_url ?? '',
              price: pr.price,
              store_prices: (p.prices ?? []).map((x: any) => ({
                store: x.store,
                price: x.price,
                unit: x.unit ?? 'stk',
                available: true,
              })) as StorePrice[],
            });
          }
        }
        setAlts(results);
      })
      .catch(() => { if (!cancelled) setAlts([]); });
    return () => { cancelled = true; };
  }, [itemName, sid]);

  return (
    <View style={styles.altPickerWrap}>
      {alts === 'loading' ? (
        <ActivityIndicator size="small" color={colors.accent} style={{ marginVertical: 8 }} />
      ) : alts.length === 0 ? (
        <Text style={styles.altNone}>Hmm, nothing similar found</Text>
      ) : (
        <FlatList
          horizontal
          showsHorizontalScrollIndicator={false}
          data={alts}
          keyExtractor={(_, i) => String(i)}
          contentContainerStyle={styles.altPickerList}
          renderItem={({ item: alt }) => {
            const isSelected = currentSelection?.name === alt.name;
            return (
              <AltCard
                key={alt.id}
                alt={alt}
                isSelected={isSelected}
                onPress={() => onSelectAlt(itemId, alt)}
              />
            );
          }}
        />
      )}
    </View>
  );
}

// ─── StoreItemRow ─────────────────────────────────────────────────────────────

function StoreItemRow({
  item,
  sid,
  matchedProduct,
  onRemoveFromCart,
  onSelectAlt,
  onIncreaseQty,
  onDecreaseQty,
}: {
  item: CartItem;
  sid: string;
  matchedProduct?: { name: string; image_url: string };
  onRemoveFromCart: () => void;
  onSelectAlt: (sid: string, itemId: string, alt: AltProduct) => void;
  onIncreaseQty: (itemId: string) => void;
  onDecreaseQty: (itemId: string) => void;
}) {
  const sp = itemPriceForStore(item, sid);
  const hasPriceData = item.store_prices != null && item.store_prices.length > 0;
  const [showAlts, setShowAlts] = useState(false);

  const currentSelection = item.store_selections?.[sid];

  const handleSelectAlt = useCallback((itemId: string, alt: AltProduct) => {
    setShowAlts(false);
    onSelectAlt(sid, itemId, alt);
  }, [sid, onSelectAlt]);

  // Prefer user selection display over AI-matched product
  const displayProduct = currentSelection
    ? { name: currentSelection.name, image_url: currentSelection.image_url ?? '' }
    : matchedProduct;

  if (!hasPriceData) {
    return (
      <View style={styles.siRowLoading}>
        <Text style={styles.storeItemName} numberOfLines={1}>{item.name}</Text>
        <Text style={styles.storeItemQty}>×{item.quantity}</Text>
        <Text style={styles.storeItemPriceUnknown}>—</Text>
      </View>
    );
  }

  if (!sp) {
    return (
      <View style={styles.siMissingWrap}>
        <View style={styles.siMissingRow}>
          <View style={styles.siMissingIcon}>
            <Ionicons name="close-circle" size={18} color={colors.warn} />
          </View>
          <View style={styles.siMissingBody}>
            <View style={styles.siMissingNameRow}>
              <Text style={styles.storeItemNameMissing} numberOfLines={1}>{item.name}</Text>
              <View style={styles.qtyControls}>
                <TouchableOpacity
                  style={styles.qtyBtn}
                  onPress={() => onDecreaseQty(item.id)}
                  hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
                >
                  <Ionicons name="remove" size={13} color={colors.accent} />
                </TouchableOpacity>
                <Text style={styles.qtyNum}>{item.quantity}</Text>
                <TouchableOpacity
                  style={[styles.qtyBtn, styles.qtyBtnFilled]}
                  onPress={() => onIncreaseQty(item.id)}
                  hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
                >
                  <Ionicons name="add" size={13} color="#fff" />
                </TouchableOpacity>
              </View>
            </View>
            <View style={styles.siMissingActions}>
              <TouchableOpacity onPress={() => setShowAlts(v => !v)} style={styles.siAltBtn}>
                <Text style={styles.siAltBtnText}>{showAlts ? 'Hide' : 'Find alternative'}</Text>
              </TouchableOpacity>
              {!showAlts && (
                <TouchableOpacity onPress={onRemoveFromCart}>
                  <Text style={styles.altRemoveBtn}>Remove</Text>
                </TouchableOpacity>
              )}
            </View>
          </View>
        </View>
        {showAlts && (
          <AlternativesPicker
            itemId={item.id}
            quantity={item.quantity}
            itemName={item.name}
            sid={sid}
            currentSelection={currentSelection}
            onRemoveFromCart={onRemoveFromCart}
            onSelectAlt={handleSelectAlt}
          />
        )}
      </View>
    );
  }

  const thumbUri = displayProduct?.image_url ? proxyImageUrl(displayProduct.image_url) : '';

  return (
    <View style={styles.siFoundWrap}>
      <View style={styles.siFoundRow}>
        {thumbUri ? (
          <Image source={{ uri: thumbUri }} style={styles.siThumb} contentFit="contain" cachePolicy="memory-disk" />
        ) : (
          <View style={[styles.siThumb, styles.siThumbEmpty]}>
            <Ionicons name="cart-outline" size={18} color={colors.text2} />
          </View>
        )}
        <View style={styles.siFoundBody}>
          <View style={styles.siFoundTopLine}>
            <Text style={styles.storeItemName} numberOfLines={1}>{item.name}</Text>
            <View style={styles.qtyControls}>
              <TouchableOpacity
                style={[styles.qtyBtn, styles.qtyBtnFilled]}
                onPress={() => onIncreaseQty(item.id)}
                hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
              >
                <Ionicons name="add" size={13} color="#fff" />
              </TouchableOpacity>
              <Text style={styles.qtyNum}>{item.quantity}</Text>
              <TouchableOpacity
                style={styles.qtyBtn}
                onPress={() => onDecreaseQty(item.id)}
                hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
              >
                <Ionicons name="remove" size={13} color={colors.accent} />
              </TouchableOpacity>
            </View>
            <Text style={styles.storeItemPrice}>€{(sp.price * item.quantity).toFixed(2)}</Text>
          </View>
          {displayProduct && (
            <Text style={styles.matchedProductName} numberOfLines={1}>
              {displayProduct.name}
            </Text>
          )}
          <TouchableOpacity onPress={() => setShowAlts(v => !v)} style={styles.siChangeBtn}>
            <Text style={styles.needAnotherBtn}>{showAlts ? 'Hide alternatives' : 'Change'}</Text>
            <Ionicons name={showAlts ? 'chevron-up' : 'chevron-down'} size={10} color={colors.accent} />
          </TouchableOpacity>
        </View>
      </View>
      {showAlts && (
        <AlternativesPicker
          itemId={item.id}
          quantity={item.quantity}
          itemName={item.name}
          sid={sid}
          currentSelection={currentSelection}
          onRemoveFromCart={onRemoveFromCart}
          onSelectAlt={handleSelectAlt}
        />
      )}
    </View>
  );
}

// ─── StoreSection ─────────────────────────────────────────────────────────────

function StoreSection({
  sid, items, total, compLoading, isExpanded, onToggle, matchedProducts, onRemoveFromCart, onSelectAlt, onIncreaseQty, onDecreaseQty, isCheapest,
}: {
  sid: string;
  items: CartItem[];
  total?: number;
  compLoading: boolean;
  isExpanded: boolean;
  onToggle: () => void;
  matchedProducts?: Map<string, { name: string; image_url: string }>;
  onRemoveFromCart: (itemId: string) => void;
  onSelectAlt: (sid: string, itemId: string, alt: AltProduct) => void;
  onIncreaseQty: (itemId: string) => void;
  onDecreaseQty: (itemId: string) => void;
  isCheapest?: boolean;
}) {
  const storeColor = STORE_COLORS[sid] ?? '#888';
  const activeColor = isCheapest ? GREEN : storeColor;
  const label = STORE_NAMES[sid] ?? sid;

  const missingCount = items.filter(item =>
    Array.isArray(item.store_prices) && !itemPriceForStore(item, sid)
  ).length;

  return (
    <View style={[
      styles.storeSection,
      isCheapest && styles.cheapestSection,
      isExpanded && { borderColor: activeColor, borderWidth: 1 },
    ]}>
      <TouchableOpacity style={styles.storeSectionHeader} onPress={onToggle} activeOpacity={0.7}>
        {STORE_LOGOS[sid] && (
          <Image source={STORE_LOGOS[sid]} style={styles.storeLogo} contentFit="contain" />
        )}
        <View style={styles.storeLabelWrap}>
          <Text style={[styles.storeLabel, (isExpanded || isCheapest) && { color: activeColor }]}>{label}</Text>
          {missingCount > 0 && !isExpanded && (
            <Text style={styles.storeMissingHint}>
              {missingCount} item{missingCount !== 1 ? 's' : ''} not available here
            </Text>
          )}
        </View>
        {isCheapest && !isExpanded && (
          <View style={styles.bestBadge}>
            <Text style={styles.bestBadgeText}>Best</Text>
          </View>
        )}
        {compLoading ? (
          <ActivityIndicator size="small" color={activeColor} style={{ marginRight: 4 }} />
        ) : total !== undefined ? (
          <Text style={[styles.storeTotal, (isExpanded || isCheapest) && { color: activeColor }]}>€{total.toFixed(2)}</Text>
        ) : null}
        <Ionicons
          name={isExpanded ? 'chevron-up' : 'chevron-down'}
          size={16}
          color={isExpanded || isCheapest ? activeColor : colors.text2}
        />
      </TouchableOpacity>

      {isExpanded && (
        <View style={styles.storeItemsWrap}>
          {items.map(item => (
            <StoreItemRow
              key={item.id}
              item={item}
              sid={sid}
              matchedProduct={matchedProducts?.get(item.id)}
              onRemoveFromCart={() => onRemoveFromCart(item.id)}
              onSelectAlt={onSelectAlt}
              onIncreaseQty={onIncreaseQty}
              onDecreaseQty={onDecreaseQty}
            />
          ))}
        </View>
      )}
    </View>
  );
}

// ─── CartScreen ───────────────────────────────────────────────────────────────

const STORE_IDS = ['rewe', 'picnic'] as const;

export default function CartScreen() {
  const { items, totalItems, removeFromCart, clearCart } = useCart();
  const { updateItem, userId } = useStore();

  const handleIncreaseQty = useCallback((itemId: string) => {
    const item = useStore.getState().items.find(i => i.id === itemId);
    if (!item) return;
    const newQty = item.quantity + 1;
    updateItem(itemId, { quantity: newQty });
    if (userId) cartApi.updateItem(itemId, { quantity: newQty }).catch(() => {});
  }, [userId, updateItem]);

  const handleDecreaseQty = useCallback((itemId: string) => {
    const item = useStore.getState().items.find(i => i.id === itemId);
    if (!item) return;
    if (item.quantity > 1) {
      const newQty = item.quantity - 1;
      updateItem(itemId, { quantity: newQty });
      if (userId) cartApi.updateItem(itemId, { quantity: newQty }).catch(() => {});
    } else {
      removeFromCart(itemId);
    }
  }, [userId, updateItem, removeFromCart]);

  const handleSelectForStore = useCallback(async (sid: string, itemId: string, alt: AltProduct) => {
    const oldItem = useStore.getState().items.find(i => i.id === itemId);

    // Optimistic local update: merge new selection + update price for this store
    const existingSelections = oldItem?.store_selections ?? {};
    const updatedSelections = {
      ...existingSelections,
      [sid]: { product_id: alt.id || undefined, name: alt.name, image_url: alt.image_url, price: alt.price },
    };
    const otherPrices = (oldItem?.store_prices ?? []).filter(p => reweNorm(p.store) !== sid);
    const updatedPrices: StorePrice[] = [
      ...otherPrices,
      { store: sid as any, price: alt.price, unit: 'stk', available: true },
    ];
    updateItem(itemId, { store_selections: updatedSelections, store_prices: updatedPrices });

    if (!userId) return;
    try {
      await cartApi.updateItem(itemId, {
        store_selection: {
          store: sid,
          product_id: alt.id || undefined,
          name: alt.name,
          image_url: alt.image_url,
          price: alt.price,
        },
      });
    } catch (err) {
      console.warn('Failed to save store selection', err);
    }
  }, [userId, updateItem]);

  const itemIds = items.map(i => i.id).join(',');
  const { comparison, loading: compLoading } = usePriceComparison(userId, totalItems, itemIds);
  const [expandedStores, setExpandedStores] = useState<string[]>([]);

  const handleClear = useCallback(() => { clearCart(); }, [clearCart]);

  // Back-fill store_prices from comparison data (cart GET never returns resolved prices).
  useEffect(() => {
    if (!comparison) return;
    const compAny = comparison as any;
    const priceMap = new Map<string, StorePrice[]>();
    for (const store of compAny.stores ?? []) {
      const sid: string = store.store ?? store.storeId ?? '';
      for (const ci of store.items ?? []) {
        const cid: string = ci.cart_item_id ?? ci.cartItemId ?? '';
        const p = ci.product?.prices?.[0];
        if (!cid || !p?.price || !sid) continue;
        const existing = priceMap.get(cid) ?? [];
        priceMap.set(cid, [...existing, { store: sid as any, price: p.price, unit: p.unit ?? 'stk', available: true }]);
      }
    }
    const snapshot = useStore.getState().items;
    for (const [cid, prices] of priceMap) {
      const item = snapshot.find(i => i.id === cid);
      if (item) {
        updateItem(cid, { store_prices: prices });
      }
    }
  }, [comparison]); // eslint-disable-line react-hooks/exhaustive-deps

  // Build matched-products map: outer key = normalized store id, inner key = cart_item_id.
  const matchedProductsByStore = useMemo(() => {
    const result = new Map<string, Map<string, { name: string; image_url: string }>>();
    if (!comparison) return result;
    const compAny = comparison as any;
    for (const store of compAny.stores ?? []) {
      const sid: string = reweNorm(store.store ?? store.storeId ?? '');
      for (const ci of store.items ?? []) {
        const cid: string = ci.cart_item_id ?? ci.cartItemId ?? '';
        const prod = ci.product;
        if (!cid || !prod) continue;
        if (!result.has(sid)) result.set(sid, new Map());
        result.get(sid)!.set(cid, { name: prod.name ?? '', image_url: proxyImageUrl(prod.image_url ?? '') });
      }
    }
    return result;
  }, [comparison]);

  const toggleStore = (sid: string) =>
    setExpandedStores(prev =>
      prev.includes(sid) ? prev.filter(s => s !== sid) : [...prev, sid]
    );

  // Compute totals locally from store_prices — consistent with per-item display.
  const hasLocalPrices = items.some(i => Array.isArray(i.store_prices));

  const getTotal = (sid: string): number | undefined => {
    if (hasLocalPrices) return computeStoreTotal(items, sid);
    return comparison?.stores.find(s => (s as any).store === sid || (s as any).storeId === sid)?.total;
  };

  const sortedStoreIds = useMemo(() => {
    return [...STORE_IDS].sort((a, b) => {
      const ta = getTotal(a) ?? Infinity;
      const tb = getTotal(b) ?? Infinity;
      return ta - tb;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, comparison]);

  const cheapestSid = sortedStoreIds.find(sid => (getTotal(sid) ?? 0) > 0);

  const savings = (() => {
    const storeTotals = STORE_IDS.map(sid => ({
      label: STORE_NAMES[sid] ?? sid,
      total: getTotal(sid) ?? 0,
    })).filter(x => x.total > 0);
    if (storeTotals.length < 2) return null;
    storeTotals.sort((a, b) => a.total - b.total);
    const diff = storeTotals[storeTotals.length - 1].total - storeTotals[0].total;
    if (diff < 0.01) return null;
    return { diff, bestName: storeTotals[0].label };
  })();

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      {items.length > 0 && (
        <View style={styles.topBar}>
          <Text style={styles.topBarTitle}>
            {items.length} item{items.length !== 1 ? 's' : ''}
          </Text>
          <TouchableOpacity onPress={handleClear} hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}>
            <Text style={styles.clearBtn}>Clear all</Text>
          </TouchableOpacity>
        </View>
      )}
      {items.length === 0 ? (
        <View style={styles.empty}>
          <Ionicons name="basket-outline" size={52} color={colors.text2} />
          <Text style={styles.emptyTitle}>Nothing here yet!</Text>
          <Text style={styles.emptyDesc}>Add something tasty to get started</Text>
          <TouchableOpacity style={styles.shopBtn} onPress={() => router.push('/')}>
            <Ionicons name="storefront-outline" size={18} color="#fff" />
            <Text style={styles.shopBtnText}>Let's Shop</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.list}>
          <View style={styles.comparisonCard}>
            {!userId ? (
              <View style={styles.signInPrompt}>
                <Ionicons name="lock-closed-outline" size={22} color={colors.text2} />
                <Text style={styles.signInText}>Sign in to see where you'll get the best deal</Text>
              </View>
            ) : (
              <>
                {savings && (
                  <View style={styles.savingsChip}>
                    <Text style={styles.savingsAmount}>Save €{savings.diff.toFixed(2)}</Text>
                    <Text style={styles.savingsLabel}>{savings.bestName} is cheapest</Text>
                  </View>
                )}
                {sortedStoreIds.map(sid => (
                  <StoreSection
                    key={sid}
                    sid={sid}
                    items={items}
                    total={getTotal(sid)}
                    compLoading={compLoading}
                    isExpanded={expandedStores.includes(sid)}
                    onToggle={() => toggleStore(sid)}
                    matchedProducts={matchedProductsByStore.get(sid)}
                    onRemoveFromCart={(itemId) => removeFromCart(itemId)}
                    onSelectAlt={handleSelectForStore}
                    onIncreaseQty={handleIncreaseQty}
                    onDecreaseQty={handleDecreaseQty}
                    isCheapest={sid === cheapestSid}
                  />
                ))}
              </>
            )}
          </View>
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

// ─── styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  topBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    backgroundColor: colors.surface,
  },
  topBarTitle: { fontSize: 15, fontFamily: fonts.semiBold, color: colors.text },
  list: { padding: 16, paddingBottom: 40 },

  // comparison card
  comparisonCard: {
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 16,
    ...shadow.sm,
  },
  signInPrompt: {
    flexDirection: 'row', alignItems: 'center', gap: 10,
    padding: 16, backgroundColor: colors.surface2, borderRadius: radius.sm,
  },
  signInText: { flex: 1, fontSize: 13, color: colors.text2 },
  savingsChip: {
    backgroundColor: GREEN_LIGHT,
    borderRadius: radius.sm,
    borderLeftWidth: 4,
    borderLeftColor: GREEN,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 14,
  },
  savingsAmount: { color: GREEN, fontSize: 22, fontFamily: fonts.bold },
  savingsLabel: { color: GREEN, fontSize: 13, fontFamily: fonts.medium, marginTop: 2 },

  // store section
  storeSection: {
    borderRadius: radius.sm, overflow: 'hidden',
    marginBottom: 8, backgroundColor: colors.surface2,
    borderColor: 'transparent', borderWidth: 1,
  },
  cheapestSection: {
    backgroundColor: GREEN_LIGHT,
    borderColor: GREEN,
    borderWidth: 1,
  },
  storeSectionHeader: {
    flexDirection: 'row', alignItems: 'center',
    paddingVertical: 12, paddingHorizontal: 14, gap: 10,
  },
  storeDot: { width: 10, height: 10, borderRadius: 5, flexShrink: 0 },
  storeLogo: { width: 28, height: 28, borderRadius: 6 },
  storeLabelWrap: { flex: 1 },
  storeLabel: { fontSize: 14, fontFamily: fonts.semiBold, color: colors.text },
  storeMissingHint: { fontSize: 11, fontFamily: fonts.medium, color: colors.warn, marginTop: 1 },
  storeTotal: { fontSize: 15, fontFamily: fonts.bold, color: colors.text },
  bestBadge: {
    backgroundColor: GREEN,
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  bestBadgeText: { color: '#fff', fontSize: 11, fontFamily: fonts.bold },

  // expanded store items
  storeItemsWrap: { paddingHorizontal: 12, paddingBottom: 12, gap: 0 },

  // loading / no-data row
  siRowLoading: {
    flexDirection: 'row', alignItems: 'center',
    paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.border, gap: 8,
  },
  storeItemName: { flex: 1, fontSize: 13, fontFamily: fonts.medium, color: colors.text },
  storeItemQty: { fontSize: 12, fontFamily: fonts.regular, color: colors.text2, minWidth: 24, textAlign: 'right' },
  storeItemPrice: { fontSize: 13, fontFamily: fonts.bold, color: colors.text },
  storeItemPriceUnknown: { fontSize: 13, color: colors.text2 },

  // missing item
  siMissingWrap: { borderTopWidth: 1, borderTopColor: colors.border },
  siMissingRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, paddingVertical: 10 },
  siMissingIcon: { marginTop: 1 },
  siMissingBody: { flex: 1, gap: 4 },
  siMissingNameRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  siMissingActions: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  siAltBtn: {
    backgroundColor: colors.accentLight, borderRadius: 8,
    paddingHorizontal: 10, paddingVertical: 4,
  },
  siAltBtnText: { fontSize: 11, fontFamily: fonts.semiBold, color: colors.accent },
  storeItemNameMissing: { fontSize: 13, fontFamily: fonts.medium, color: colors.text2, textDecorationLine: 'line-through' },
  altRemoveBtn: { fontSize: 11, fontFamily: fonts.medium, color: colors.warn },

  // found item
  siFoundWrap: { borderTopWidth: 1, borderTopColor: colors.border },
  siFoundRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, paddingVertical: 10 },
  siThumb: { width: 44, height: 44, borderRadius: 10, backgroundColor: colors.surface2, flexShrink: 0 },
  siThumbEmpty: { alignItems: 'center', justifyContent: 'center' },
  siFoundBody: { flex: 1, gap: 3 },
  siFoundTopLine: { flexDirection: 'row', alignItems: 'flex-start', gap: 8 },
  siChangeBtn: { flexDirection: 'row', alignItems: 'center', gap: 3, marginTop: 2 },

  // quantity controls
  qtyControls: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  qtyBtn: {
    width: 24, height: 24, borderRadius: 12,
    borderWidth: 1.5, borderColor: colors.accent,
    alignItems: 'center', justifyContent: 'center',
  },
  qtyBtnFilled: { backgroundColor: colors.accent, borderWidth: 0 },
  qtyNum: { fontSize: 13, fontFamily: fonts.bold, color: colors.text, minWidth: 16, textAlign: 'center' },

  // alternatives picker
  altPickerWrap: { marginTop: 4 },
  altPickerList: { paddingVertical: 8, gap: 8 },
  altCard: {
    width: 130, height: 180,
    backgroundColor: colors.surface, borderRadius: radius.sm,
    padding: 8, alignItems: 'center', justifyContent: 'flex-start', ...shadow.sm,
  },
  altCardName: { fontSize: 12, fontFamily: fonts.regular, color: colors.text, marginTop: 6, textAlign: 'center' },
  altCardPrice: { fontSize: 13, fontFamily: fonts.bold, color: colors.accent, marginTop: 4 },
  altCardSelected: { borderWidth: 2, borderColor: colors.accent },
  altCardSelectedBadge: { fontSize: 11, fontFamily: fonts.bold, color: colors.accent, marginTop: 2 },
  altNone: { fontSize: 11, fontFamily: fonts.regular, color: colors.text2, marginLeft: 20 },

  // "Change" toggle
  needAnotherBtn: { fontSize: 11, fontFamily: fonts.semiBold, color: colors.accent },
  matchedProductName: { fontSize: 11, fontFamily: fonts.regular, color: colors.text2, fontStyle: 'italic' },

  clearBtn: { fontSize: 13, color: colors.warn, fontFamily: fonts.semiBold },

  // empty state
  empty: { flex: 1, alignItems: 'center', paddingTop: 80, gap: 10 },
  emptyTitle: { fontSize: 20, fontFamily: fonts.bold, color: colors.text },
  emptyDesc: { fontSize: 14, fontFamily: fonts.regular, color: colors.text2 },
  shopBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 8,
    backgroundColor: colors.accent, paddingHorizontal: 24,
    paddingVertical: 12, borderRadius: radius.md, marginTop: 8, ...shadow.sm,
  },
  shopBtnText: { color: '#fff', fontSize: 16, fontFamily: fonts.bold },
});
