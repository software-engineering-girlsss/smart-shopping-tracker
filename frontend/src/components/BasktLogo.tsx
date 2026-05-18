import React, { useId, useEffect, useState } from 'react';
import { View, Text, StyleSheet, Platform } from 'react-native';
import Svg, { Path, Rect, Line, G, Defs, ClipPath } from 'react-native-svg';
import { fonts } from '../theme';

// Body:   #F2EDE8  warm beige
// Grid:   #8B7B6E  muted brown, opacity 0.55
// Rim:    #1B4332  dark green  (on dark bg: #2D6A4F)
// Handle: #E8622A  brand orange (on orange bg: #FDFAF8)
// Text:   #E8622A  on light/beige  |  #FDFAF8 on dark/orange

type Layout = 'horizontal' | 'stacked' | 'icon';
type Variant = 'light' | 'dark' | 'orange';

interface Props {
  iconSize?: number;
  wordmarkSize?: number;
  layout?: Layout;
  variant?: Variant;
}

// SVG favicon data URI for web <head>
export const BASKT_FAVICON_SVG =
  `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">` +
  `<defs><clipPath id="c"><path d="M 8 40 L 92 40 L 82 90 Q 82 94 77 94 L 23 94 Q 18 94 18 90 Z"/></clipPath></defs>` +
  `<path d="M 21 45 C 21 12 79 12 79 45" stroke="%23E8622A" stroke-width="7.5" fill="none" stroke-linecap="round"/>` +
  `<path d="M 8 40 L 92 40 L 82 90 Q 82 94 77 94 L 23 94 Q 18 94 18 90 Z" fill="%23F2EDE8"/>` +
  `<g clip-path="url(%23c)" stroke="%238B7B6E" stroke-width="1.6" fill="none" opacity="0.55">` +
  `<line x1="0" y1="54" x2="100" y2="54"/><line x1="0" y1="68" x2="100" y2="68"/>` +
  `<line x1="0" y1="82" x2="100" y2="82"/>` +
  `<line x1="30" y1="38" x2="27" y2="96"/><line x1="50" y1="38" x2="50" y2="96"/>` +
  `<line x1="70" y1="38" x2="73" y2="96"/>` +
  `</g>` +
  `<rect x="6" y="34" width="88" height="12" rx="4" fill="%231B4332"/>` +
  `</svg>`;

function BasktIcon({ size, handleColor, bodyFill, gridColor, gridOpacity, rimFill }: {
  size: number;
  handleColor: string;
  bodyFill: string;
  gridColor: string;
  gridOpacity: number;
  rimFill: string;
}) {
  // react-native-svg doesn't serialize <defs>/<clipPath> correctly during SSR
  // (Expo static export pre-renders HTML server-side). Defer SVG to client only
  // so server and client render the same placeholder, eliminating the hydration mismatch.
  const [mounted, setMounted] = useState(Platform.OS !== 'web');
  useEffect(() => { setMounted(true); }, []);

  const uid = useId().replace(/:/g, '');
  const clipId = `bclip-${uid}`;

  if (!mounted) {
    return <View style={{ width: size, height: size }} />;
  }

  return (
    <Svg viewBox="0 0 100 100" width={size} height={size}>
      <Defs>
        <ClipPath id={clipId}>
          <Path d="M 8 40 L 92 40 L 82 90 Q 82 94 77 94 L 23 94 Q 18 94 18 90 Z" />
        </ClipPath>
      </Defs>
      <Path
        d="M 21 45 C 21 12 79 12 79 45"
        stroke={handleColor}
        strokeWidth="7.5"
        fill="none"
        strokeLinecap="round"
      />
      <Path
        d="M 8 40 L 92 40 L 82 90 Q 82 94 77 94 L 23 94 Q 18 94 18 90 Z"
        fill={bodyFill}
      />
      <G clipPath={`url(#${clipId})`} stroke={gridColor} strokeWidth="1.6" opacity={gridOpacity}>
        <Line x1="0" y1="54" x2="100" y2="54" />
        <Line x1="0" y1="68" x2="100" y2="68" />
        <Line x1="0" y1="82" x2="100" y2="82" />
        <Line x1="30" y1="38" x2="27" y2="96" />
        <Line x1="50" y1="38" x2="50" y2="96" />
        <Line x1="70" y1="38" x2="73" y2="96" />
      </G>
      <Rect x="6" y="34" width="88" height="12" rx="4" fill={rimFill} />
    </Svg>
  );
}

function resolveTokens(variant: Variant) {
  if (variant === 'dark') {
    return {
      handleColor: '#E8622A',
      bodyFill:    '#F2EDE8',
      gridColor:   '#8B7B6E',
      gridOpacity: 0.55,
      rimFill:     '#2D6A4F',
      textColor:   '#FDFAF8',
      mutedColor:  'rgba(253,250,248,0.6)',
    };
  }
  if (variant === 'orange') {
    return {
      handleColor: '#FDFAF8',
      bodyFill:    '#FDFAF8',
      gridColor:   '#C04E1E',
      gridOpacity: 0.35,
      rimFill:     '#C04E1E',
      textColor:   '#FDFAF8',
      mutedColor:  'rgba(253,250,248,0.75)',
    };
  }
  // light (default)
  return {
    handleColor: '#E8622A',
    bodyFill:    '#F2EDE8',
    gridColor:   '#8B7B6E',
    gridOpacity: 0.55,
    rimFill:     '#1B4332',
    textColor:   '#E8622A',
    mutedColor:  '#8B7B6E',
  };
}

export function BasktLogo({ iconSize = 32, wordmarkSize, layout = 'horizontal', variant = 'light' }: Props) {
  const t = resolveTokens(variant);
  const wSize = wordmarkSize ?? iconSize * 0.7;

  const icon = (
    <BasktIcon
      size={iconSize}
      handleColor={t.handleColor}
      bodyFill={t.bodyFill}
      gridColor={t.gridColor}
      gridOpacity={t.gridOpacity}
      rimFill={t.rimFill}
    />
  );

  if (layout === 'icon') return icon;

  if (layout === 'stacked') {
    return (
      <View style={styles.stacked}>
        {icon}
        <Text style={[styles.wordmark, { color: t.textColor, fontSize: iconSize * 0.52 }]}>
          baskt
        </Text>
        <Text style={[styles.tagline, { color: t.mutedColor }]}>
          Smart shopping · Real savings
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.row}>
      {icon}
      <Text style={[styles.wordmark, { color: t.textColor, fontSize: wSize }]}>
        baskt
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  stacked: {
    alignItems: 'center',
    gap: 6,
  },
  wordmark: {
    fontFamily: fonts.black,
    letterSpacing: -0.5,
  },
  tagline: {
    fontFamily: fonts.semiBold,
    fontSize: 12,
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
});
