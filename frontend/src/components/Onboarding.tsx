import React, { useRef, useState } from 'react';
import {
  Animated,
  Dimensions,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { colors, fonts, radius, shadow } from '../theme';
import { BasktLogo } from './BasktLogo';

const { width: SCREEN_W } = Dimensions.get('window');
const BOX_W = Math.min(SCREEN_W - 48, 400);
const CARD_W = (BOX_W - 24 - 10) / 2; // mirrors index.tsx grid: padding 12, gap 10

const GREEN = '#16A34A';
const GREEN_LIGHT = '#F0FDF4';

// Generic delivery service identifiers — no real brand names
const SVC: [string, string] = ['#4F7EFF', '#06B6D4'];

// ─── Slide 1: Shop / browse ───────────────────────────────────────────────────

function ShopPreview() {
  const cards = [
    { emoji: '🥛', name: 'Fresh Whole\nMilk 1L', price: 1.29 },
    { emoji: '🍞', name: 'Sourdough\nBread 750g', price: 2.49 },
  ];

  return (
    <View style={pr.screen}>
      {/* Search bar — mirrors index.tsx searchContainer */}
      <View style={pr.searchContainer}>
        <View style={pr.searchWrap}>
          <Ionicons name="search-outline" size={16} color={colors.text2} style={{ marginRight: 10 }} />
          <Text style={pr.searchInput}>Search groceries…</Text>
          <View style={pr.searchBtn}>
            <Ionicons name="search" size={14} color="#fff" />
          </View>
        </View>
      </View>

      {/* 2-column grid — mirrors ProductCard */}
      <View style={pr.grid}>
        {cards.map((c, i) => (
          <View key={i} style={pr.card}>
            {/* image area */}
            <View style={pr.imageWrap}>
              <Text style={{ fontSize: 38 }}>{c.emoji}</Text>
            </View>
            {/* name */}
            <Text style={pr.cardName}>{c.name}</Text>
            {/* price row */}
            <View style={pr.priceRow}>
              <Text style={pr.cardPrice}>€{c.price.toFixed(2)}</Text>
              <View style={pr.storeTags}>
                {SVC.map((col, j) => (
                  <View key={j} style={[pr.storeTag, { backgroundColor: col }]}>
                    <Text style={pr.storeTagText}>{j === 0 ? 'A' : 'B'}</Text>
                  </View>
                ))}
              </View>
            </View>
            {/* actions */}
            <View style={pr.actions}>
              <Ionicons name="heart-outline" size={18} color={colors.text2} />
              <View style={pr.addBtn}>
                <Ionicons name="add" size={16} color="#fff" />
              </View>
            </View>
          </View>
        ))}
      </View>
    </View>
  );
}

// ─── Slide 2: Price comparison ────────────────────────────────────────────────

function ComparePreview() {
  const stores = [
    { color: SVC[0], label: 'Delivery A', total: 18.43, cheapest: true },
    { color: SVC[1], label: 'Delivery B', total: 22.64, cheapest: false },
  ];

  return (
    <View style={[pr.screen, { padding: 16 }]}>
      {/* Savings chip — exact copy of cart.tsx savingsChip */}
      <View style={pr.savingsChip}>
        <Text style={pr.savingsAmount}>Save €4.21</Text>
        <Text style={pr.savingsLabel}>Delivery A is cheapest</Text>
      </View>

      {/* Store sections — mirrors StoreSection header */}
      {stores.map((s, i) => (
        <View
          key={i}
          style={[
            pr.storeSection,
            s.cheapest && pr.cheapestSection,
          ]}
        >
          <View style={pr.storeSectionHeader}>
            {/* placeholder for store logo */}
            <View style={[pr.storeLogo, { backgroundColor: s.color + '22' }]}>
              <Ionicons name="bag-handle-outline" size={15} color={s.color} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={[pr.storeLabel, s.cheapest && { color: GREEN }]}>{s.label}</Text>
            </View>
            {s.cheapest && (
              <View style={pr.bestBadge}>
                <Text style={pr.bestBadgeText}>Best</Text>
              </View>
            )}
            <Text style={[pr.storeTotal, s.cheapest && { color: GREEN }]}>
              €{s.total.toFixed(2)}
            </Text>
            <Ionicons
              name="chevron-down"
              size={16}
              color={s.cheapest ? GREEN : colors.text2}
            />
          </View>
        </View>
      ))}
    </View>
  );
}

// ─── Slide 3: Auto-alternative ────────────────────────────────────────────────

function AltPreview() {
  return (
    <View style={[pr.screen, { padding: 0 }]}>
      {/* Store section header (collapsed look) */}
      <View style={[pr.storeSection, { borderRadius: 0 }]}>
        <View style={pr.storeSectionHeader}>
          <View style={[pr.storeLogo, { backgroundColor: SVC[0] + '22' }]}>
            <Ionicons name="bag-handle-outline" size={15} color={SVC[0]} />
          </View>
          <Text style={[pr.storeLabel, { flex: 1 }]}>Delivery A</Text>
          <Text style={pr.storeTotal}>€12.80</Text>
          <Ionicons name="chevron-up" size={16} color={SVC[0]} />
        </View>
      </View>

      <View style={{ paddingHorizontal: 12, paddingBottom: 12 }}>
        {/* Normal found item — mirrors siFoundWrap */}
        <View style={pr.siFoundWrap}>
          <View style={pr.siFoundRow}>
            <View style={pr.siThumb}>
              <Ionicons name="cart-outline" size={18} color={colors.text2} />
            </View>
            <View style={{ flex: 1, gap: 2 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <Text style={[pr.siName, { flex: 1 }]} numberOfLines={1}>Penne Pasta 500g</Text>
                <Text style={pr.siQty}>×1</Text>
                <Text style={pr.siPrice}>€0.89</Text>
              </View>
              <Text style={pr.siMatched}>Barilla Penne Rigate</Text>
            </View>
          </View>
        </View>

        {/* Auto-suggested item — mirrors siAutoAltBanner + siFoundWrap */}
        <View style={pr.siFoundWrap}>
          {/* Banner — exact siAutoAltBanner style */}
          <View style={pr.siAutoAltBanner}>
            <Ionicons name="information-circle-outline" size={13} color={colors.accent} />
            <Text style={pr.siAutoAltBannerText}>
              Not available here — showing nearest alternative
            </Text>
          </View>
          <View style={pr.siFoundRow}>
            <View style={pr.siThumb}>
              <Ionicons name="cart-outline" size={18} color={colors.text2} />
            </View>
            <View style={{ flex: 1, gap: 2 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <Text style={[pr.siName, { flex: 1 }]} numberOfLines={1}>Cherry Tomatoes</Text>
                <Text style={pr.siQty}>×1</Text>
                <Text style={pr.siPrice}>€2.49</Text>
              </View>
              <Text style={pr.siMatched}>Tomatoes 500g</Text>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 3 }}>
                <Text style={pr.siChange}>Change</Text>
                <Ionicons name="chevron-down" size={10} color={colors.accent} />
              </View>
            </View>
          </View>
        </View>
      </View>
    </View>
  );
}

// ─── Slides config ────────────────────────────────────────────────────────────

const SLIDES = [
  {
    accent: colors.accent,
    title: 'Browse & compare\nprices instantly',
    description: 'Search for any grocery and see prices side by side across delivery services.',
    Preview: ShopPreview,
  },
  {
    accent: GREEN,
    title: 'See where\nto shop',
    description: 'Your cart shows exactly how much you save at the cheapest delivery service.',
    Preview: ComparePreview,
  },
  {
    accent: colors.accent,
    title: 'Never miss\nan item',
    description: "When a product isn't available, we automatically find the nearest alternative.",
    Preview: AltPreview,
  },
];

// ─── Main component ───────────────────────────────────────────────────────────

interface Props {
  onDone: () => void;
}

export function Onboarding({ onDone }: Props) {
  const [step, setStep] = useState(0);
  const fadeAnim = useRef(new Animated.Value(1)).current;
  const slideAnim = useRef(new Animated.Value(0)).current;

  const slide = SLIDES[step];
  const isLast = step === SLIDES.length - 1;

  const goTo = (next: number) => {
    Animated.parallel([
      Animated.timing(fadeAnim, { toValue: 0, duration: 130, useNativeDriver: true }),
      Animated.timing(slideAnim, { toValue: -20, duration: 130, useNativeDriver: true }),
    ]).start(() => {
      setStep(next);
      slideAnim.setValue(20);
      Animated.parallel([
        Animated.timing(fadeAnim, { toValue: 1, duration: 180, useNativeDriver: true }),
        Animated.timing(slideAnim, { toValue: 0, duration: 180, useNativeDriver: true }),
      ]).start();
    });
  };

  return (
    <SafeAreaView style={styles.root}>
      {/* Top bar */}
      <View style={styles.topBar}>
        <BasktLogo layout="horizontal" iconSize={22} wordmarkSize={20} />
        <TouchableOpacity onPress={onDone} hitSlop={{ top: 14, bottom: 14, left: 14, right: 14 }}>
          <Text style={styles.skipText}>Skip</Text>
        </TouchableOpacity>
      </View>

      {/* Animated slide */}
      <Animated.View
        style={[
          styles.slideWrap,
          { opacity: fadeAnim, transform: [{ translateY: slideAnim }] },
        ]}
      >
        {/* Preview box — phone-UI mockup */}
        <View style={styles.previewBox}>
          <slide.Preview />
        </View>

        {/* Accent line */}
        <View style={[styles.accentLine, { backgroundColor: slide.accent }]} />

        <Text style={styles.title}>{slide.title}</Text>
        <Text style={styles.description}>{slide.description}</Text>
      </Animated.View>

      {/* Dots */}
      <View style={styles.dots}>
        {SLIDES.map((_, i) => (
          <View
            key={i}
            style={[
              styles.dot,
              i === step && { width: 20, backgroundColor: slide.accent },
            ]}
          />
        ))}
      </View>

      {/* Footer */}
      <View style={styles.footer}>
        <TouchableOpacity
          style={[styles.nextBtn, { backgroundColor: slide.accent }]}
          onPress={() => (isLast ? onDone() : goTo(step + 1))}
          activeOpacity={0.85}
        >
          <Text style={styles.nextBtnText}>{isLast ? 'Get started' : 'Next'}</Text>
          <Ionicons name={isLast ? 'checkmark' : 'arrow-forward'} size={18} color="#fff" />
        </TouchableOpacity>
        {!isLast && (
          <TouchableOpacity onPress={onDone} style={styles.laterBtn}>
            <Text style={styles.laterText}>I'll explore on my own</Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

// ─── Preview styles (exact copies from real screens) ─────────────────────────

const pr = StyleSheet.create({
  screen: {
    backgroundColor: colors.bg,
    overflow: 'hidden',
  },

  // index.tsx: searchContainer / searchWrap / searchInput / searchBtn
  searchContainer: {
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  searchWrap: { flexDirection: 'row', alignItems: 'center' },
  searchInput: { flex: 1, fontSize: 14, color: colors.text2, fontFamily: fonts.regular },
  searchBtn: {
    backgroundColor: colors.accent,
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },

  // index.tsx: grid / row → ProductCard layout
  grid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 12,
    gap: 10,
  },
  card: {
    width: CARD_W,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 12,
    ...shadow.sm,
  },
  imageWrap: {
    height: 70,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  cardName: {
    fontSize: 12,
    fontFamily: fonts.semiBold,
    color: colors.text,
    lineHeight: 17,
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 6,
  },
  cardPrice: {
    fontSize: 14,
    fontFamily: fonts.bold,
    color: colors.accent,
  },
  storeTags: { flexDirection: 'row', gap: 4 },
  storeTag: { paddingHorizontal: 5, paddingVertical: 2, borderRadius: 6 },
  storeTagText: { color: '#fff', fontSize: 9, fontFamily: fonts.semiBold },
  actions: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 8,
    marginTop: 10,
  },
  addBtn: {
    backgroundColor: colors.accent,
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },

  // cart.tsx: savingsChip / savingsAmount / savingsLabel
  savingsChip: {
    backgroundColor: GREEN_LIGHT,
    borderRadius: radius.sm,
    borderLeftWidth: 4,
    borderLeftColor: GREEN,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginBottom: 12,
  },
  savingsAmount: { color: GREEN, fontSize: 18, fontFamily: fonts.bold },
  savingsLabel: { color: GREEN, fontSize: 12, fontFamily: fonts.medium, marginTop: 1 },

  // cart.tsx: storeSection / cheapestSection / storeSectionHeader / storeLogo / storeLabel / storeTotal / bestBadge
  storeSection: {
    borderRadius: radius.sm,
    backgroundColor: colors.surface2,
    borderColor: 'transparent',
    borderWidth: 1,
    marginBottom: 8,
    overflow: 'hidden',
  },
  cheapestSection: {
    backgroundColor: GREEN_LIGHT,
    borderColor: GREEN,
  },
  storeSectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 14,
    gap: 10,
  },
  storeLogo: {
    width: 28,
    height: 28,
    borderRadius: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  storeLabel: { fontSize: 14, fontFamily: fonts.semiBold, color: colors.text },
  storeTotal: { fontSize: 14, fontFamily: fonts.bold, color: colors.text },
  bestBadge: {
    backgroundColor: GREEN,
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  bestBadgeText: { color: '#fff', fontSize: 10, fontFamily: fonts.bold },

  // cart.tsx: siFoundWrap / siFoundRow / siThumb / siName / siQty / siPrice / siMatched
  siFoundWrap: { borderTopWidth: 1, borderTopColor: colors.border },
  siFoundRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    paddingVertical: 10,
  },
  siThumb: {
    width: 40,
    height: 40,
    borderRadius: 10,
    backgroundColor: colors.surface2,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  siName: { fontSize: 12, fontFamily: fonts.medium, color: colors.text },
  siQty: { fontSize: 11, fontFamily: fonts.regular, color: colors.text2 },
  siPrice: { fontSize: 12, fontFamily: fonts.bold, color: colors.text },
  siMatched: { fontSize: 10, fontFamily: fonts.regular, color: colors.text2, fontStyle: 'italic' },
  siChange: { fontSize: 10, fontFamily: fonts.semiBold, color: colors.accent },

  // cart.tsx: siAutoAltBanner / siAutoAltBannerText
  siAutoAltBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingTop: 8,
    paddingBottom: 0,
  },
  siAutoAltBannerText: {
    flex: 1,
    fontSize: 10,
    fontFamily: fonts.regular,
    color: colors.accent,
  },
});

// ─── Shell styles ─────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },

  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 8,
  },
  skipText: { fontSize: 14, fontFamily: fonts.medium, color: colors.text2 },

  slideWrap: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },

  previewBox: {
    width: BOX_W,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
    ...shadow.lg,
  },

  accentLine: { width: 32, height: 4, borderRadius: 2, marginTop: 20, marginBottom: 14 },

  title: {
    fontSize: 26,
    fontFamily: fonts.black,
    color: colors.text,
    textAlign: 'center',
    lineHeight: 33,
    marginBottom: 10,
  },
  description: {
    fontSize: 14,
    fontFamily: fonts.regular,
    color: colors.text2,
    textAlign: 'center',
    lineHeight: 21,
    maxWidth: 300,
  },

  dots: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
    paddingBottom: 20,
  },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.border },

  footer: { paddingHorizontal: 24, paddingBottom: 12, gap: 10 },

  nextBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 15,
    borderRadius: radius.md,
    ...shadow.sm,
  },
  nextBtnText: { fontSize: 16, fontFamily: fonts.bold, color: '#fff' },

  laterBtn: { alignItems: 'center', paddingVertical: 6 },
  laterText: { fontSize: 13, fontFamily: fonts.regular, color: colors.text2 },
});
