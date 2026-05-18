// app/deals.tsx
import { Ionicons } from '@expo/vector-icons';
import React, { useEffect, useState } from 'react';
import {
    ActivityIndicator,
    FlatList,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { productsApi } from '../src/api/client';
import { colors, radius, shadow, STORE_COLORS, STORE_NAMES } from '../src/theme';
import type { Product, StoreId } from '../src/types';

interface Deal {
  id: string;
  product_id: string;
  productName: string;
  store: StoreId;
  original_price: number;
  promo_price: number;
  discount_percent: number;
  valid_until?: string;
}

const STORE_FILTERS: { id: string; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'rewe', label: 'REWE' },
  { id: 'rewe_online', label: 'REWE Online' },
  { id: 'picnic', label: 'Picnic' },
];

function extractDeals(products: Product[]): Deal[] {
  const deals: Deal[] = [];
  for (const p of products) {
    for (const price of p.prices ?? []) {
      const promo: any = (price as any).promotion;
      if (promo) {
        const original = promo.original_price ?? price.price;
        const promoPrice = price.price;
        if (original > promoPrice) {
          deals.push({
            id: `${p.id}:${price.store}`,
            product_id: p.id,
            productName: p.name,
            store: price.store,
            original_price: original,
            promo_price: promoPrice,
            discount_percent: Math.round(((original - promoPrice) / original) * 100),
            valid_until: promo.valid_until,
          });
        }
      }
    }
  }
  return deals;
}

export default function DealsScreen() {
  const [activeStore, setActiveStore] = useState<string>('all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deals, setDeals] = useState<Deal[]>([]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    productsApi.featured()
      .then(data => {
        if (cancelled) return;
        const arr = Array.isArray(data) ? data : (data as any)?.items ?? [];
        setDeals(extractDeals(arr as Product[]));
      })
      .catch(err => { if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const filtered = activeStore === 'all' ? deals : deals.filter(d => d.store === activeStore);

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <View style={styles.filterScroll}>
        <FlatList
          data={STORE_FILTERS}
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filterList}
          keyExtractor={s => s.id}
          renderItem={({ item: s }) => (
            <TouchableOpacity
              style={[styles.filterChip, activeStore === s.id && styles.filterChipActive]}
              onPress={() => setActiveStore(s.id)}
            >
              {s.id !== 'all' && (
                <View style={[styles.storeDot, { backgroundColor: STORE_COLORS[s.id] ?? '#888' }]} />
              )}
              <Text style={[styles.filterText, activeStore === s.id && styles.filterTextActive]}>
                {s.label}
              </Text>
            </TouchableOpacity>
          )}
        />
      </View>

      {loading ? (
        <View style={styles.empty}><ActivityIndicator color={colors.accent} /></View>
      ) : error ? (
        <View style={styles.empty}>
          <Ionicons name="warning-outline" size={52} color={colors.text2} />
          <Text style={styles.emptyTitle}>{error}</Text>
          <Text style={styles.emptyDesc}>Sign in to see deals from the backend.</Text>
        </View>
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={d => d.id}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <Text style={styles.count}>
              {filtered.length} deal{filtered.length !== 1 ? 's' : ''} available
            </Text>
          }
          ListEmptyComponent={
            <View style={styles.empty}>
              <Ionicons name="pricetag-outline" size={52} color={colors.text2} />
              <Text style={styles.emptyTitle}>No active deals</Text>
              <Text style={styles.emptyDesc}>Check back later for new offers</Text>
            </View>
          }
          renderItem={({ item: d }) => {
            const storeColor = STORE_COLORS[d.store] ?? '#888';
            const storeName = STORE_NAMES[d.store] ?? d.store;
            const validDate = d.valid_until
              ? new Date(d.valid_until).toLocaleDateString('de-DE', { day: 'numeric', month: 'short' })
              : null;
            return (
              <View style={styles.dealCard}>
                <View style={styles.dealLeft}>
                  <Ionicons name="cart-outline" size={28} color={colors.text2} />
                </View>
                <View style={styles.dealInfo}>
                  <Text style={styles.dealName} numberOfLines={2}>{d.productName}</Text>
                  <View style={styles.storeRow}>
                    <View style={[styles.storeDot, { backgroundColor: storeColor }]} />
                    <Text style={styles.dealStore}>{storeName}</Text>
                    {validDate && <Text style={styles.dealValid}>· bis {validDate}</Text>}
                  </View>
                  <View style={styles.priceRow}>
                    <Text style={styles.oldPrice}>€{d.original_price.toFixed(2)}</Text>
                    <Text style={styles.newPrice}>€{d.promo_price.toFixed(2)}</Text>
                  </View>
                </View>
                <View style={[styles.pctBadge, { backgroundColor: storeColor }]}>
                  <Text style={styles.pctText}>-{d.discount_percent}%</Text>
                </View>
              </View>
            );
          }}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  filterScroll: {
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  filterList: { paddingHorizontal: 16, paddingVertical: 12, gap: 8 },
  filterChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 20,
    backgroundColor: colors.surface2,
    borderWidth: 1.5,
    borderColor: colors.border,
    gap: 6,
  },
  filterChipActive: { backgroundColor: colors.accentLight, borderColor: colors.accent },
  storeDot: { width: 8, height: 8, borderRadius: 4 },
  filterText: { fontSize: 13, fontWeight: '500', color: colors.text2 },
  filterTextActive: { color: colors.accent, fontWeight: '700' },
  list: { padding: 16, paddingBottom: 40 },
  count: { fontSize: 13, color: colors.text2, marginBottom: 14, fontWeight: '500' },
  dealCard: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 14,
    marginBottom: 10,
    alignItems: 'center',
    gap: 12,
    ...shadow.sm,
  },
  dealLeft: {
    width: 52, height: 52,
    backgroundColor: colors.surface2,
    borderRadius: radius.sm,
    alignItems: 'center', justifyContent: 'center',
  },
  dealInfo: { flex: 1 },
  dealName: { fontSize: 15, fontWeight: '600', color: colors.text, marginBottom: 4 },
  storeRow: { flexDirection: 'row', alignItems: 'center', gap: 5, marginBottom: 6 },
  dealStore: { fontSize: 12, fontWeight: '600', color: colors.text2 },
  dealValid: { fontSize: 11, color: colors.text2 },
  priceRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  oldPrice: { fontSize: 13, color: colors.text2, textDecorationLine: 'line-through' },
  newPrice: { fontSize: 16, fontWeight: '700', color: colors.accent },
  pctBadge: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: radius.sm },
  pctText: { color: '#fff', fontSize: 13, fontWeight: '800' },
  empty: { flex: 1, alignItems: 'center', paddingTop: 80, gap: 10 },
  emptyTitle: { fontSize: 18, fontWeight: '700', color: colors.text, textAlign: 'center', paddingHorizontal: 24 },
  emptyDesc: { fontSize: 14, color: colors.text2, textAlign: 'center', paddingHorizontal: 40 },
});
