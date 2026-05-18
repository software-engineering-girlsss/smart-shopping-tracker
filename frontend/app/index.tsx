// app/index.tsx — Home / Shop screen
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
    ActivityIndicator,
    FlatList,
    Pressable,
    SectionList,
    StyleSheet,
    Text,
    TextInput,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { CategoryPills } from '../src/components/CategoryPills';
import { ProductCard } from '../src/components/ProductCard';
import { ProductDetailModal } from '../src/components';
import { useCart, useFavorites, useSearch } from '../src/hooks';
import { useStore } from '../src/store';
import { colors, radius, shadow } from '../src/theme';
import { categoriesApi, productsApi } from '../src/api/client';
import type { Product } from '../src/types';
import type { Category } from '../src/types';

const KNOWN_STORE_COUNT = 2;

type Section = { title: string; slug: string; data: Product[] };

export default function HomeScreen() {
  const [featured, setFeatured] = useState<Product[]>([]);
  const [featuredLoading, setFeaturedLoading] = useState(true);
  const [featuredError, setFeaturedError] = useState<string | null>(null);
  const [detailProduct, setDetailProduct] = useState<Product | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [categoryProducts, setCategoryProducts] = useState<Product[]>([]);
  const [categoryLoading, setCategoryLoading] = useState(false);
  const [genericAdding, setGenericAdding] = useState(false);

  const { userId, items: cartItems } = useStore();

  const loadFeatured = useCallback(() => {
    setFeaturedLoading(true);
    setFeaturedError(null);
    productsApi.genericFeatured()
      .then(data => setFeatured(data as Product[]))
      .catch(err => setFeaturedError(err instanceof Error ? err.message : 'Failed to load products'))
      .finally(() => setFeaturedLoading(false));
  }, []);

  const loadCategories = useCallback(() => {
    categoriesApi.list()
      .then(data => setCategories(data as Category[]))
      .catch(() => { /* non-critical */ });
  }, []);

  useEffect(() => { loadFeatured(); loadCategories(); }, [loadFeatured, loadCategories, userId]);

  useEffect(() => {
    if (!selectedCategory) { setCategoryProducts([]); return; }
    setCategoryLoading(true);
    categoriesApi.getProducts(selectedCategory)
      .then(data => setCategoryProducts(data as Product[]))
      .catch(() => setCategoryProducts([]))
      .finally(() => setCategoryLoading(false));
  }, [selectedCategory]);

  const { query, setQuery, committedQuery, results, loading: searchLoading, triggerSearch, clearSearch } = useSearch();
  const { addToCart, decreaseFromCart, addGenericItem } = useCart();
  const { toggle: toggleFavorite, isFavorite } = useFavorites();

  const sortedFeatured = useMemo(
    () => [...featured].sort((a, b) => ((b as any)._priority ?? 0) - ((a as any)._priority ?? 0)),
    [featured],
  );

  // Group featured products by their category field into sections
  const featuredSections = useMemo((): Section[] => {
    if (sortedFeatured.every(p => !p.category)) {
      return [{ title: '', slug: '', data: sortedFeatured }];
    }
    const order: string[] = [];
    const groups: Record<string, { title: string; slug: string; products: Product[] }> = {};
    for (const p of sortedFeatured) {
      const slug = p.category || 'other';
      if (!groups[slug]) {
        const cat = categories.find(c => c.slug === slug);
        const title = cat ? cat.name : slug;
        groups[slug] = { title, slug, products: [] };
        order.push(slug);
      }
      groups[slug].products.push(p);
    }
    return order.map(slug => ({ title: groups[slug].title, slug, data: groups[slug].products }));
  }, [sortedFeatured, categories]);

  const handleAdd = useCallback((product: Product) => {
    addToCart(product);
  }, [addToCart]);

  const handleRemove = useCallback((product: Product) => {
    decreaseFromCart(product);
  }, [decreaseFromCart]);

  const handleAddGeneric = useCallback(async () => {
    if (!committedQuery.trim() || genericAdding) return;
    setGenericAdding(true);
    try {
      await addGenericItem(committedQuery.trim());
    } catch { /* toast handled by global handler */ } finally {
      setGenericAdding(false);
    }
  }, [committedQuery, genericAdding, addGenericItem]);

  const handleCategorySelect = useCallback((slug: string | null) => {
    setSelectedCategory(slug);
    clearSearch();
  }, [clearSearch]);

  const isSearchActive = !!committedQuery.trim();
  const isCategoryActive = !!selectedCategory && !isSearchActive;

  const activeProducts: Product[] = isSearchActive ? results
    : isCategoryActive ? categoryProducts
    : [];

  const showLoader = !isSearchActive && !isCategoryActive && featuredLoading;
  const showError  = !isSearchActive && !isCategoryActive && !featuredLoading && !!featuredError;
  const showFeaturedSections = !isSearchActive && !isCategoryActive && !featuredLoading && !featuredError;
  const showActiveList = (isSearchActive || isCategoryActive) && !showLoader;

  const renderProductItem = ({ item }: { item: Product }) => (
    <View style={{ flex: 1, maxWidth: '50%', alignSelf: 'stretch' }}>
      <ProductCard
        product={item}
        onAdd={handleAdd}
        onRemove={handleRemove}
        onToggleFav={toggleFavorite}
        isFavorite={isFavorite(item.id)}
        cartQuantity={cartItems.find(ci => ci.product_id === item.id)?.quantity || 0}
        onPress={(p) => setDetailProduct(p)}
      />
    </View>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* Search Bar */}
      <View style={styles.searchContainer}>
        <View style={styles.searchWrap}>
          <Ionicons name="search" size={18} color={colors.text2} style={styles.searchIcon} />
          <TextInput
            style={styles.searchInput}
            placeholder="Search products…"
            placeholderTextColor={colors.text2}
            value={query}
            onChangeText={setQuery}
            returnKeyType="search"
            onSubmitEditing={triggerSearch}
            onKeyPress={(e: any) => {
              if (e.nativeEvent?.key === 'Enter') triggerSearch();
            }}
          />
          {searchLoading ? (
            <ActivityIndicator size="small" color={colors.accent} style={{ marginRight: 6 }} />
          ) : (
            <View style={styles.searchRightGroup}>
              {query.length > 0 && (
                <TouchableOpacity
                  onPress={clearSearch}
                  hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                >
                  <Ionicons name="close-circle" size={18} color={colors.text2} />
                </TouchableOpacity>
              )}
              <TouchableOpacity style={styles.searchBtn} onPress={triggerSearch}>
                <Ionicons name="arrow-forward" size={15} color="#fff" />
              </TouchableOpacity>
            </View>
          )}
        </View>
      </View>

      {/* Category Pills */}
      {categories.length > 0 && (
        <View style={styles.pillsWrapper}>
          <CategoryPills
            categories={categories}
            selected={selectedCategory}
            onSelect={handleCategorySelect}
          />
        </View>
      )}

      {/* Add generic product to cart — shown when search has a query */}
      {isSearchActive && committedQuery.trim().length > 0 && (
        <TouchableOpacity
          style={styles.genericAddBar}
          onPress={handleAddGeneric}
          disabled={genericAdding}
          activeOpacity={0.8}
        >
          {genericAdding ? (
            <ActivityIndicator size="small" color={colors.accent} />
          ) : (
            <Ionicons name="add-circle-outline" size={18} color={colors.accent} />
          )}
          <Text style={styles.genericAddText}>
            Add "{committedQuery.trim()}" to list
          </Text>
        </TouchableOpacity>
      )}

      {/* Content */}
      {showLoader ? (
        <View style={styles.empty}>
          <ActivityIndicator color={colors.accent} size="large" />
        </View>
      ) : showError ? (
        <View style={styles.empty}>
          <Ionicons name="warning-outline" size={40} color={colors.text2} />
          <Text style={styles.emptyText}>{featuredError}</Text>
          <Text style={styles.emptyHint}>
            {userId ? 'Try again.' : 'Sign in on the Account tab to see products.'}
          </Text>
          <TouchableOpacity style={styles.retryBtn} onPress={loadFeatured}>
            <Text style={styles.retryText}>Retry</Text>
          </TouchableOpacity>
        </View>
      ) : showFeaturedSections ? (
        featuredSections.length === 1 && !featuredSections[0].title ? (
          // No category info: flat grid
          <FlatList
            data={featuredSections[0].data}
            keyExtractor={item => item.id}
            numColumns={2}
            contentContainerStyle={styles.grid}
            columnWrapperStyle={styles.row}
            renderItem={renderProductItem}
            ListEmptyComponent={
              <View style={styles.empty}>
                <Ionicons name="basket-outline" size={40} color={colors.text2} />
                <Text style={styles.emptyText}>No products available</Text>
              </View>
            }
          />
        ) : (
          // Category sections
          <SectionList
            sections={featuredSections}
            keyExtractor={item => item.id}
            renderItem={({ item, index, section }) => {
              const isOdd = index % 2 === 0;
              if (!isOdd) return null; // render pairs in renderItem of even index
              const next = section.data[index + 1];
              return (
                <View style={styles.row}>
                  <View style={{ flex: 1, maxWidth: '50%', alignSelf: 'stretch' }}>
                    <ProductCard
                      product={item}
                      onAdd={handleAdd}
                      onRemove={handleRemove}
                      onToggleFav={toggleFavorite}
                      isFavorite={isFavorite(item.id)}
                      cartQuantity={cartItems.find(ci => ci.product_id === item.id)?.quantity || 0}
                      onPress={(p) => setDetailProduct(p)}
                    />
                  </View>
                  {next ? (
                    <View style={{ flex: 1, maxWidth: '50%', alignSelf: 'stretch' }}>
                      <ProductCard
                        product={next}
                        onAdd={handleAdd}
                        onRemove={handleRemove}
                        onToggleFav={toggleFavorite}
                        isFavorite={isFavorite(next.id)}
                        cartQuantity={cartItems.find(ci => ci.product_id === next.id)?.quantity || 0}
                        onPress={(p) => setDetailProduct(p)}
                      />
                    </View>
                  ) : <View style={{ flex: 1, maxWidth: '50%' }} />}
                </View>
              );
            }}
            renderSectionHeader={({ section }) =>
              section.title ? (
                <View style={styles.sectionHeader}>
                  <Text style={styles.sectionTitle}>{section.title}</Text>
                </View>
              ) : null
            }
            contentContainerStyle={styles.grid}
            stickySectionHeadersEnabled={false}
          />
        )
      ) : showActiveList ? (
        categoryLoading ? (
          <View style={styles.empty}>
            <ActivityIndicator color={colors.accent} size="large" />
          </View>
        ) : (
          <FlatList
            data={activeProducts}
            keyExtractor={item => item.id}
            numColumns={2}
            contentContainerStyle={styles.grid}
            columnWrapperStyle={styles.row}
            ListEmptyComponent={
              isSearchActive && !searchLoading ? (
                <View style={styles.empty}>
                  <Ionicons name="search-outline" size={40} color={colors.text2} />
                  <Text style={styles.emptyText}>No results for "{committedQuery}"</Text>
                </View>
              ) : isCategoryActive ? (
                <View style={styles.empty}>
                  <Ionicons name="basket-outline" size={40} color={colors.text2} />
                  <Text style={styles.emptyText}>No products in this category yet</Text>
                  <Text style={styles.emptyHint}>Check back after the daily catalogue refresh</Text>
                </View>
              ) : null
            }
            renderItem={renderProductItem}
          />
        )
      ) : null}

      <ProductDetailModal
        visible={!!detailProduct}
        product={detailProduct}
        onClose={() => setDetailProduct(null)}
        onAdd={handleAdd}
      />

      {cartItems.length > 0 && (
        <Pressable
          style={({ pressed }: any) => [styles.compareBar, pressed && { opacity: 0.9 }]}
          onPress={() => router.push('/cart')}
          accessibilityRole="button"
        >
          <View style={styles.compareLeft}>
            <Ionicons name="basket" size={20} color="#fff" />
            <Text style={styles.compareCount}>
              {cartItems.length} item{cartItems.length !== 1 ? 's' : ''}
            </Text>
          </View>
          <Text style={styles.compareLabel}>
            Compare prices in {KNOWN_STORE_COUNT} shops
          </Text>
          <Ionicons name="chevron-forward" size={18} color="#fff" />
        </Pressable>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  searchContainer: {
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  searchWrap: { flexDirection: 'row', alignItems: 'center' },
  searchIcon: { marginRight: 10 },
  searchInput: { flex: 1, fontSize: 16, color: colors.text, paddingVertical: 6 },
  searchRightGroup: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  searchBtn: {
    backgroundColor: colors.accent,
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillsWrapper: {
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  genericAddBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: colors.accentLight,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  genericAddText: {
    fontSize: 14,
    color: colors.accent,
    fontWeight: '600',
  },
  grid: { padding: 12, paddingBottom: 100 },
  row: { gap: 10, marginBottom: 10, flexDirection: 'row' },
  sectionHeader: {
    paddingTop: 16,
    paddingBottom: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: colors.text,
    textTransform: 'capitalize',
  },
  empty: { flex: 1, alignItems: 'center', paddingTop: 64, gap: 12 },
  emptyText: { fontSize: 16, color: colors.text2, textAlign: 'center', paddingHorizontal: 24 },
  emptyHint: { fontSize: 13, color: colors.text2, textAlign: 'center', paddingHorizontal: 32 },
  retryBtn: {
    marginTop: 8,
    backgroundColor: colors.accent,
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 8,
  },
  retryText: { color: '#fff', fontSize: 14, fontWeight: '700' },
  compareBar: {
    position: 'absolute',
    left: 12,
    right: 12,
    bottom: 12,
    backgroundColor: colors.accent,
    borderRadius: radius.md,
    paddingVertical: 12,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    ...shadow.lg,
  },
  compareLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  compareCount: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
  },
  compareLabel: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
    flex: 1,
    textAlign: 'center',
  },
});
