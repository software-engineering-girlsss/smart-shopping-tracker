// src/components/PriceComparison.tsx
import { Ionicons } from '@expo/vector-icons';
import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Linking,
  ActivityIndicator,
} from 'react-native';
import { colors, radius, shadow, STORE_COLORS, STORE_NAMES } from '../theme';
import type { CartPriceComparison } from '../types';

interface Props {
  comparison: CartPriceComparison | null;
  loading: boolean;
}

export function PriceComparison({ comparison, loading }: Props) {
  if (loading) {
    return (
      <View style={styles.loadingWrap}>
        <ActivityIndicator color={colors.accent} />
        <Text style={styles.loadingText}>Comparing prices…</Text>
      </View>
    );
  }

  const stores = comparison?.stores ?? [];
  if (!comparison || stores.length === 0) {
    return (
      <View style={styles.emptyWrap}>
        <Ionicons name="cash-outline" size={32} color={colors.text2} />
        <Text style={styles.emptyText}>Add items and we'll find you the best price</Text>
      </View>
    );
  }

  const sorted = [...stores].sort((a, b) => (a.total ?? 0) - (b.total ?? 0));
  const best = sorted[0];
  const worst = sorted[sorted.length - 1];
  const savings = (worst?.total ?? 0) - (best?.total ?? 0);

  return (
    <View>
      {savings > 0.01 && best && (
        <View style={styles.savingsChip}>
          <Text style={styles.savingsText}>
            Save €{savings.toFixed(2)} · {STORE_NAMES[best.store] ?? (best as any).store_name ?? best.store} cheapest
          </Text>
        </View>
      )}

      {sorted.map((s: any, i) => {
        const isBest = i === 0;
        const storeColor = STORE_COLORS[s.store] ?? '#888';
        const storeLabel = STORE_NAMES[s.store] ?? s.store_name ?? s.store;
        const total = typeof s.total === 'number' ? s.total : 0;
        const missing = s.missing_items ?? (Array.isArray(s.missing) ? s.missing.length : 0);
        return (
          <View
            key={s.store ?? i}
            style={[styles.storeRow, isBest && styles.bestRow]}
          >
            <View style={[styles.dot, { backgroundColor: storeColor }]} />
            <View style={styles.storeInfo}>
              <Text style={[styles.storeName, isBest && styles.bestName]}>
                {storeLabel}
                {s.type === 'online' && (
                  <Text style={styles.onlineTag}> · online</Text>
                )}
              </Text>
              {missing > 0 && (
                <Text style={styles.missing}>{missing} items unavailable</Text>
              )}
            </View>
            {isBest && (
              <View style={styles.bestBadge}>
                <Text style={styles.bestBadgeText}>Best</Text>
              </View>
            )}
            <Text style={[styles.price, isBest && styles.bestPrice]}>
              €{total.toFixed(2)}
            </Text>

            {s.deeplink && (
              <TouchableOpacity
                style={styles.orderBtn}
                onPress={() => Linking.openURL(s.deeplink!)}
              >
                <Text style={styles.orderBtnText}>Order</Text>
              </TouchableOpacity>
            )}
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  loadingWrap: {
    padding: 24,
    alignItems: 'center',
    gap: 10,
  },
  loadingText: {
    color: colors.text2,
    fontSize: 14,
  },
  emptyWrap: {
    padding: 32,
    alignItems: 'center',
    gap: 8,
  },
  emptyText: {
    color: colors.text2,
    fontSize: 14,
    textAlign: 'center',
  },
  savingsChip: {
    backgroundColor: colors.accentLight,
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 8,
    marginBottom: 16,
    alignSelf: 'flex-start',
  },
  savingsText: {
    color: colors.accent,
    fontSize: 13,
    fontWeight: '600',
  },
  storeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 14,
    borderRadius: radius.sm,
    marginBottom: 8,
    backgroundColor: colors.surface2,
    gap: 10,
  },
  bestRow: {
    backgroundColor: colors.accentLight,
    ...shadow.sm,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    flexShrink: 0,
  },
  storeInfo: {
    flex: 1,
  },
  storeName: {
    fontSize: 14,
    fontWeight: '500',
    color: colors.text,
  },
  bestName: {
    fontWeight: '700',
    color: colors.accent,
  },
  onlineTag: {
    fontSize: 11,
    color: colors.text2,
    fontWeight: '400',
  },
  missing: {
    fontSize: 11,
    color: colors.warn,
    marginTop: 1,
  },
  bestBadge: {
    backgroundColor: colors.accent,
    borderRadius: 12,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  bestBadgeText: {
    color: '#fff',
    fontSize: 11,
    fontWeight: '700',
  },
  price: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
  },
  bestPrice: {
    color: colors.accent,
    fontSize: 16,
    fontWeight: '700',
  },
  orderBtn: {
    backgroundColor: colors.accent,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
  },
  orderBtnText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
});
