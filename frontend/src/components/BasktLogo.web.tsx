// BasktLogo.web.tsx — web-only override (Metro picks this over BasktLogo.tsx on web)
// Uses a data-URI SVG instead of react-native-svg, which can fail in static Expo builds.
import React from 'react';
import { Image, View, Text, StyleSheet } from 'react-native';
import { fonts } from '../theme';

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

type Layout = 'horizontal' | 'stacked' | 'icon';
type Variant = 'light' | 'dark' | 'orange';

interface Props {
  iconSize?: number;
  wordmarkSize?: number;
  layout?: Layout;
  variant?: Variant;
}

function resolveTokens(variant: Variant) {
  if (variant === 'dark') {
    return {
      handleColor: '#E8622A', bodyFill: '#F2EDE8', gridColor: '#8B7B6E',
      gridOpacity: 0.55, rimFill: '#2D6A4F',
      textColor: '#FDFAF8', mutedColor: 'rgba(253,250,248,0.6)',
    };
  }
  if (variant === 'orange') {
    return {
      handleColor: '#FDFAF8', bodyFill: '#FDFAF8', gridColor: '#C04E1E',
      gridOpacity: 0.35, rimFill: '#C04E1E',
      textColor: '#FDFAF8', mutedColor: 'rgba(253,250,248,0.75)',
    };
  }
  return {
    handleColor: '#E8622A', bodyFill: '#F2EDE8', gridColor: '#8B7B6E',
    gridOpacity: 0.55, rimFill: '#1B4332',
    textColor: '#E8622A', mutedColor: '#8B7B6E',
  };
}

function makeSvgUri(
  handleColor: string, bodyFill: string,
  gridColor: string, gridOpacity: number, rimFill: string,
): string {
  const svg = [
    `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">`,
    `<defs><clipPath id="c">`,
    `<path d="M 8 40 L 92 40 L 82 90 Q 82 94 77 94 L 23 94 Q 18 94 18 90 Z"/>`,
    `</clipPath></defs>`,
    `<path d="M 21 45 C 21 12 79 12 79 45" stroke="${handleColor}" stroke-width="7.5" fill="none" stroke-linecap="round"/>`,
    `<path d="M 8 40 L 92 40 L 82 90 Q 82 94 77 94 L 23 94 Q 18 94 18 90 Z" fill="${bodyFill}"/>`,
    `<g clip-path="url(#c)" stroke="${gridColor}" stroke-width="1.6" fill="none" opacity="${gridOpacity}">`,
    `<line x1="0" y1="54" x2="100" y2="54"/>`,
    `<line x1="0" y1="68" x2="100" y2="68"/>`,
    `<line x1="0" y1="82" x2="100" y2="82"/>`,
    `<line x1="30" y1="38" x2="27" y2="96"/>`,
    `<line x1="50" y1="38" x2="50" y2="96"/>`,
    `<line x1="70" y1="38" x2="73" y2="96"/>`,
    `</g>`,
    `<rect x="6" y="34" width="88" height="12" rx="4" fill="${rimFill}"/>`,
    `</svg>`,
  ].join('');
  return `data:image/svg+xml;base64,${btoa(svg)}`;
}

export function BasktLogo({ iconSize = 32, wordmarkSize, layout = 'horizontal', variant = 'light' }: Props) {
  const t = resolveTokens(variant);
  const wSize = wordmarkSize ?? iconSize * 0.7;
  const uri = makeSvgUri(t.handleColor, t.bodyFill, t.gridColor, t.gridOpacity, t.rimFill);

  const icon = (
    <Image
      source={{ uri }}
      style={{ width: iconSize, height: iconSize }}
      resizeMode="contain"
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
  row: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  stacked: { alignItems: 'center', gap: 6 },
  wordmark: { fontFamily: fonts.black, letterSpacing: -0.5 },
  tagline: { fontFamily: fonts.semiBold, fontSize: 12, letterSpacing: 0.8, textTransform: 'uppercase' },
});
