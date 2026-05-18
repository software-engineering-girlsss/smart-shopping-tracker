// src/components/ProductCard.tsx
import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import React, { useState } from 'react';
import {
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { proxyImageUrl } from '../api/client';
import { colors, radius, shadow } from '../theme';
import type { Product } from '../types';

interface Props {
  product: Product;
  onAdd: (product: Product) => void;
  onRemove?: (product: Product) => void;
  onToggleFav: (product: Product) => void;
  isFavorite: boolean;
  cartQuantity?: number;
  onPress?: (product: Product) => void;
}

export function ProductCard({ product, onAdd, onRemove, onToggleFav, isFavorite, cartQuantity, onPress }: Props) {
  const [imgError, setImgError] = useState(false);
  const imgUri = product.image_url ? proxyImageUrl(product.image_url) : '';
  const showImage = !!imgUri && !imgError;

  const CardWrapper = onPress ? TouchableOpacity : View;
  return (
    <CardWrapper
      style={styles.card}
      onPress={onPress ? () => onPress(product) : undefined}
      activeOpacity={0.92}
    >
      {product.promotion && (
        <View style={styles.badge}>
          <Text style={styles.badgeText}>-{product.promotion.discount_percent}%</Text>
        </View>
      )}

      <View style={styles.imageWrap}>
        {showImage ? (
          <Image
            source={{ uri: imgUri }}
            style={styles.image}
            contentFit="contain"
            cachePolicy="memory-disk"
            onError={() => setImgError(true)}
            {...(Platform.OS === 'web' ? { referrerPolicy: 'no-referrer' } : {})}
          />
        ) : (
          <Ionicons name="cart-outline" size={40} color={colors.text2} />
        )}
      </View>

      <View style={styles.info}>
        <Text style={styles.name} numberOfLines={2}>{product.name}</Text>
        <Text style={styles.brand}>{product.brand}</Text>

        <View style={styles.priceRow}>
          {product.best_price ? (
            <Text style={styles.price}>€{product.best_price.price.toFixed(2)}</Text>
          ) : (
            <Text style={styles.store}>No price</Text>
          )}
        </View>
      </View>

      <View style={styles.actions}>
        <TouchableOpacity
          style={styles.favBtn}
          onPress={() => onToggleFav(product)}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
        >
          <Ionicons
            name={isFavorite ? 'heart' : 'heart-outline'}
            size={20}
            color={isFavorite ? colors.warn : colors.text2}
          />
        </TouchableOpacity>

        <View style={styles.quantityControls}>
          <TouchableOpacity
            style={[styles.addBtn, { backgroundColor: colors.accent }]}
            onPress={() => onAdd(product)}
          >
            <Ionicons name="add" size={18} color="#fff" />
          </TouchableOpacity>
          {typeof cartQuantity === 'number' && cartQuantity > 0 && (
            <Text style={styles.quantityText}>{cartQuantity}</Text>
          )}
          <TouchableOpacity
            style={[styles.removeBtn, cartQuantity === 0 && styles.removeBtnDisabled]}
            onPress={() => onRemove?.(product)}
            disabled={!cartQuantity}
          >
            <Ionicons name="remove" size={18} color={colors.accent} />
          </TouchableOpacity>
        </View>
      </View>
    </CardWrapper>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 12,
    marginBottom: 12,
    ...shadow.sm,
    position: 'relative',
  },
  badge: {
    position: 'absolute',
    top: 10,
    left: 10,
    backgroundColor: colors.warn,
    borderRadius: 20,
    paddingHorizontal: 8,
    paddingVertical: 2,
    zIndex: 1,
  },
  badgeText: {
    color: '#fff',
    fontSize: 11,
    fontWeight: '700',
  },
  imageWrap: {
    width: '100%',
    aspectRatio: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  image: {
    width: '100%',
    height: '100%',
  },
  info: {
    flex: 1,
  },
  name: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.text,
    lineHeight: 19,
  },
  brand: {
    fontSize: 12,
    color: colors.text2,
    marginTop: 2,
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 6,
  },
  price: {
    fontSize: 16,
    fontWeight: '700',
    color: colors.accent,
  },
  store: {
    fontSize: 10,
    fontWeight: '600',
    color: colors.text2,
    backgroundColor: colors.surface2,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
  },
  storeTags: {
    flexDirection: 'row',
    gap: 4,
  },
  storeTag: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
  },
  storeTagText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: '600',
  },
  actions: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 8,
    marginTop: 4,
  },
  favBtn: {
    padding: 4,
  },
  quantityControls: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  removeBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  removeBtnDisabled: {
    opacity: 0.3,
  },
  quantityText: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.text,
    minWidth: 16,
    textAlign: 'center',
  },
  addBtn: {
    backgroundColor: colors.accent,
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
