// src/components/ProductDetailModal.tsx
import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import React from 'react';
import {
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { proxyImageUrl } from '../api/client';
import { colors, radius, shadow, STORE_COLORS, STORE_NAMES } from '../theme';
import type { Product, StorePrice } from '../types';

interface Props {
  product: Product | null;
  visible: boolean;
  onClose: () => void;
  onAdd: (product: Product) => void;
}

export function ProductDetailModal({ product, visible, onClose, onAdd }: Props) {
  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle="pageSheet"
      transparent={false}
      onRequestClose={onClose}
    >
      <SafeAreaView style={styles.safeArea} edges={['bottom']}>
        <View style={styles.container}>
          {/* Header */}
          <View style={styles.header}>
            <Text style={styles.title} numberOfLines={2}>
              {product?.name ?? ''}
            </Text>
            <TouchableOpacity
              style={styles.closeBtn}
              onPress={onClose}
              hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            >
              <Ionicons name="close" size={24} color={colors.text} />
            </TouchableOpacity>
          </View>

          <ScrollView
            style={styles.scroll}
            contentContainerStyle={styles.scrollContent}
            showsVerticalScrollIndicator={false}
          >
            {/* Image */}
            <View style={styles.imageWrap}>
              {product?.image_url ? (
                <Image
                  source={{ uri: proxyImageUrl(product.image_url) }}
                  style={styles.image}
                  contentFit="contain"
                  cachePolicy="memory-disk"
                />
              ) : (
                <Ionicons name="cart-outline" size={64} color={colors.text2} />
              )}
            </View>

            {/* Brand + Category */}
            <View style={styles.metaRow}>
              {product?.brand ? (
                <Text style={styles.metaText}>{product.brand}</Text>
              ) : null}
              {product?.brand && product?.category ? (
                <Text style={styles.metaDot}>·</Text>
              ) : null}
              {product?.category ? (
                <Text style={styles.metaText}>{product.category}</Text>
              ) : null}
            </View>

            {/* Store Prices */}
            {product && product.prices.length > 0 && (
              <View style={styles.pricesSection}>
                <Text style={styles.pricesSectionTitle}>Prices</Text>
                {product.prices.map((sp: StorePrice) => {
                  const sid: string = sp.store ?? '';
                  const storeName = STORE_NAMES[sid] ?? sid;
                  const storeColor = STORE_COLORS[sid] ?? colors.surface2;
                  return (
                    <View key={sid} style={styles.priceRow}>
                      <View style={[styles.storeBadge, { backgroundColor: storeColor }]}>
                        <Text style={styles.storeBadgeText}>{storeName}</Text>
                      </View>
                      <Text style={styles.priceText}>
                        €{sp.price.toFixed(2)}
                      </Text>
                      {sp.unit ? (
                        <Text style={styles.unitText}>{sp.unit}</Text>
                      ) : null}
                      {!sp.available && (
                        <Text style={styles.unavailableText}>Unavailable</Text>
                      )}
                    </View>
                  );
                })}
              </View>
            )}
          </ScrollView>

          {/* Add to Cart Button */}
          <View style={styles.footer}>
            <TouchableOpacity
              style={styles.addBtn}
              onPress={() => {
                if (product) onAdd(product);
                onClose();
              }}
              activeOpacity={0.85}
            >
              <Text style={styles.addBtnText}>Add to Cart</Text>
            </TouchableOpacity>
          </View>
        </View>
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  container: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    gap: 12,
  },
  title: {
    flex: 1,
    fontSize: 18,
    fontWeight: '700',
    color: colors.text,
    lineHeight: 24,
  },
  closeBtn: {
    padding: 2,
    marginTop: 1,
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 16,
  },
  imageWrap: {
    height: 180,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    marginBottom: 16,
    ...shadow.sm,
  },
  image: {
    width: '100%',
    height: 180,
    borderRadius: radius.md,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 20,
    flexWrap: 'wrap',
  },
  metaText: {
    fontSize: 13,
    color: colors.text2,
  },
  metaDot: {
    fontSize: 13,
    color: colors.text2,
  },
  pricesSection: {
    gap: 10,
  },
  pricesSectionTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: colors.text,
    marginBottom: 4,
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: colors.surface,
    borderRadius: radius.sm,
    paddingHorizontal: 14,
    paddingVertical: 12,
    ...shadow.sm,
  },
  storeBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  storeBadgeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '700',
  },
  priceText: {
    flex: 1,
    fontSize: 16,
    fontWeight: '700',
    color: colors.accent,
  },
  unitText: {
    fontSize: 12,
    color: colors.text2,
  },
  unavailableText: {
    fontSize: 11,
    color: colors.warn,
    fontWeight: '600',
  },
  footer: {
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 16,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  addBtn: {
    backgroundColor: colors.accent,
    borderRadius: radius.md,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  addBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
});
