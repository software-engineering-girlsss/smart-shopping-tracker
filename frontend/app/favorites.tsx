// app/favorites.tsx
import { Ionicons } from '@expo/vector-icons';
import React, { useCallback } from 'react';
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ProductCard } from '../src/components/ProductCard';
import { useCart, useFavorites } from '../src/hooks';
import { useStore } from '../src/store';
import { colors } from '../src/theme';
import type { Product } from '../src/types';

export default function FavoritesScreen() {
  const { userId } = useStore();
  const favoriteProductsCache = useStore(s => s.favoriteProductsCache);
  const { favorites, toggle: toggleFavorite, isFavorite, refresh, loading } = useFavorites();
  const { addToCart } = useCart();

  // Build the displayed list from cache; skip IDs with no cached data.
  const favProducts: Product[] = favorites
    .map(id => favoriteProductsCache[id])
    .filter(Boolean) as Product[];

  const handleRefresh = useCallback(async () => {
    await refresh();
  }, [refresh]);

  const isLoading = loading;

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      {isLoading ? (
        <View style={styles.centered}>
          <ActivityIndicator color={colors.accent} />
        </View>
      ) : (
        <FlatList
          data={favProducts}
          keyExtractor={item => item.id}
          numColumns={2}
          contentContainerStyle={styles.grid}
          columnWrapperStyle={favProducts.length > 0 ? styles.row : undefined}
          onRefresh={handleRefresh}
          refreshing={false}
          ListEmptyComponent={
            <View style={styles.centered}>
              <Ionicons name="heart" size={52} color={colors.warn} />
              <Text style={styles.emptyTitle}>No favourites yet</Text>
              <Text style={styles.emptyDesc}>
                {userId
                  ? 'Tap the heart on a product to save it here'
                  : 'Sign in to sync your favourites across devices'}
              </Text>
            </View>
          }
          renderItem={({ item }) => (
            <View style={{ flex: 1, maxWidth: '50%' }}>
              <ProductCard
                product={item}
                onAdd={addToCart}
                onToggleFav={toggleFavorite}
                isFavorite={isFavorite(item.id)}
              />
            </View>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  grid: { padding: 12, paddingBottom: 40, flexGrow: 1 },
  row: { gap: 10 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingTop: 80, gap: 10 },
  emptyTitle: { fontSize: 20, fontWeight: '700', color: colors.text },
  emptyDesc: { fontSize: 14, color: colors.text2, textAlign: 'center', paddingHorizontal: 40 },
});
