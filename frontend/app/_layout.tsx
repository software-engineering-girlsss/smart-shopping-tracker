// app/_layout.tsx
import { Tabs, usePathname } from 'expo-router';
import Head from 'expo-router/head';
import { Ionicons } from '@expo/vector-icons';
import {
  Poppins_400Regular,
  Poppins_500Medium,
  Poppins_600SemiBold,
  Poppins_700Bold,
  Poppins_800ExtraBold,
  Poppins_900Black,
} from '@expo-google-fonts/poppins';
import { useEffect, useState } from 'react';
import { Text, View, ActivityIndicator, ScrollView, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { colors } from '../src/theme';
import { useStore } from '../src/store';
import { authApi, BASE_URL, healthApi, setUnauthorizedHandler } from '../src/api/client';
import { AuthForm, BasktLogo, Onboarding } from '../src/components';
import { ClientOnly } from '../src/utils/ClientOnly';
import { useFonts } from 'expo-font';

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    ...Ionicons.font,
    Poppins_400Regular,
    Poppins_500Medium,
    Poppins_600SemiBold,
    Poppins_700Bold,
    Poppins_800ExtraBold,
    Poppins_900Black,
  });
  const pathname = usePathname();
  const totalItems = useStore(s => s.items.reduce((sum, i) => sum + i.quantity, 0));
  const userId = useStore(s => s.userId);
  const setUser = useStore(s => s.setUser);
  const clearUser = useStore(s => s.clearUser);
  const clearCart = useStore(s => s.clearCart);
  const setPicnicConnection = useStore(s => s.setPicnicConnection);
  const clearPicnicConnection = useStore(s => s.clearPicnicConnection);
  const [backendStatus, setBackendStatus] = useState<'checking' | 'up' | 'down'>('checking');
  const [authReady, setAuthReady] = useState(false);
  const [onboarded, setOnboarded] = useState(false);
  const [onboardingReady, setOnboardingReady] = useState(false);
  const [emailJustConfirmed, setEmailJustConfirmed] = useState(false);
  const [showStatusBar, setShowStatusBar] = useState(false);

  // Verify backend is reachable on every app load — visible in Network tab.
  useEffect(() => {
    healthApi.check()
      .then(() => setBackendStatus('up'))
      .catch(() => setBackendStatus('down'));
  }, []);

  // Check whether the user has already seen the onboarding screens.
  useEffect(() => {
    AsyncStorage.getItem('onboarding_done')
      .then(v => setOnboarded(v === '1'))
      .finally(() => setOnboardingReady(true));
  }, []);

  useEffect(() => {
    if (Platform.OS !== 'web') return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.shiftKey && e.key === 'S') {
        e.preventDefault();
        setShowStatusBar(v => !v);
      }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, []);

  // Detect email confirmation redirect in THIS tab (Tab 2 scenario) and notify all other open tabs.
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    const hash = typeof window !== 'undefined' ? window.location.hash : '';
    const search = typeof window !== 'undefined' ? window.location.search : '';
    const hashParams = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : '');
    const searchParams = new URLSearchParams(search);
    const confirmedViaHash = hashParams.get('type') === 'signup' || hashParams.get('type') === 'email';
    const confirmedViaQuery = searchParams.get('email_confirmed') === '1' || searchParams.get('confirmed') === '1';
    if (confirmedViaHash || confirmedViaQuery) {
      setEmailJustConfirmed(true);
      window.history.replaceState(null, '', window.location.pathname);
      // Notify all other open tabs (e.g. the original registration tab)
      try {
        const bc = new BroadcastChannel('baskt_auth');
        bc.postMessage({ type: 'EMAIL_CONFIRMED' });
        bc.close();
      } catch {}
      // localStorage fallback for browsers without BroadcastChannel
      try {
        localStorage.setItem('baskt_email_confirmed', Date.now().toString());
        setTimeout(() => localStorage.removeItem('baskt_email_confirmed'), 10000);
      } catch {}
    }
  }, []);

  // Listen for email confirmation signal from other tabs (Tab 1 scenario).
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    let bc: BroadcastChannel | null = null;
    try {
      bc = new BroadcastChannel('baskt_auth');
      bc.onmessage = (e: MessageEvent) => {
        if (e.data?.type === 'EMAIL_CONFIRMED') setEmailJustConfirmed(true);
      };
    } catch {}
    const handleStorage = (e: StorageEvent) => {
      if (e.key === 'baskt_email_confirmed' && e.newValue) setEmailJustConfirmed(true);
    };
    window.addEventListener('storage', handleStorage);
    return () => {
      bc?.close();
      window.removeEventListener('storage', handleStorage);
    };
  }, []);

  // Auto-logout on any 401 response from the backend.
  useEffect(() => {
    setUnauthorizedHandler(async () => {
      await AsyncStorage.removeItem('auth_token');
      await AsyncStorage.removeItem('refresh_token');
      clearUser();
      clearPicnicConnection();
    });
    return () => setUnauthorizedHandler(null);
  }, [clearUser]);

  // On startup, verify the stored token via /users/me. If invalid, clear auth.
  // The 15s abort ensures the spinner never hangs when the backend is slow to respond.
  useEffect(() => {
    const controller = new AbortController();
    const fallback = setTimeout(() => controller.abort(), 15_000);
    (async () => {
      const token = await AsyncStorage.getItem('auth_token');
      if (!token) {
        setAuthReady(true);
        return;
      }
      try {
        const me = await authApi.me(controller.signal);
        setUser(String(me.id), me.name, me.email);
        const picnic = me.connected_accounts?.find(a => a.provider === 'picnic');
        if (picnic) setPicnicConnection(picnic.email, picnic.zip_code ?? undefined);
        else clearPicnicConnection();
      } catch {
        await AsyncStorage.removeItem('auth_token');
        await AsyncStorage.removeItem('refresh_token');
        clearUser();
        clearCart();
        clearPicnicConnection();
      } finally {
        clearTimeout(fallback);
        setAuthReady(true);
      }
    })();
    return () => { controller.abort(); clearTimeout(fallback); };
  }, [setUser, clearUser, clearCart]);

  if (!fontsLoaded || !authReady || !onboardingReady) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg }}>
        <ActivityIndicator color={colors.accent} />
      </View>
    );
  }

  if (!userId && pathname !== '/auth/callback') {
    if (!onboarded) {
      return (
        <Onboarding
          onDone={async () => {
            await AsyncStorage.setItem('onboarding_done', '1');
            setOnboarded(true);
          }}
        />
      );
    }

    const handleAuthSuccess = async (id: string, name: string, token: string) => {
      await AsyncStorage.setItem('auth_token', token);
      setUser(id, name);
    };

    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: colors.bg }}>
        <ScrollView
          contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', padding: 24 }}
          keyboardShouldPersistTaps="handled"
        >
          <AuthForm onSuccess={handleAuthSuccess} emailJustConfirmed={emailJustConfirmed} />
        </ScrollView>
      </SafeAreaView>
    );
  }

  const statusColor =
    backendStatus === 'up' ? '#2D6A4F' : backendStatus === 'down' ? '#D62828' : '#6B6457';
  const statusLabel =
    backendStatus === 'up' ? 'Connected' : backendStatus === 'down' ? 'Unreachable' : 'Checking…';

  return (
    <>
    {Platform.OS === 'web' && (
      <Head>
        <title>Baskt</title>
        <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
        <link rel="manifest" href="/manifest.json" />
        <meta name="theme-color" content="#E8622A" />
      </Head>
    )}
    <ClientOnly>
      {showStatusBar && (
        <View style={{ backgroundColor: statusColor, paddingVertical: 4, paddingHorizontal: 12 }}>
          <Text style={{ color: '#fff', fontSize: 11, textAlign: 'center' }}>
            {statusLabel}
          </Text>
        </View>
      )}
    </ClientOnly>
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: colors.accent,
        tabBarInactiveTintColor: colors.text2,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.border,
          borderTopWidth: 1,
          paddingBottom: 6,
          height: 60,
        },
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '600',
          marginTop: -2,
        },
        headerStyle: {
          backgroundColor: colors.surface,
          borderBottomColor: colors.border,
          borderBottomWidth: 1,
        },
        headerTitleStyle: {
          fontSize: 24,
          color: colors.accent,
          fontWeight: '900',
          letterSpacing: -0.5,
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          headerTitle: () => <BasktLogo iconSize={26} wordmarkSize={24} />,
          tabBarLabel: 'Shop',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="storefront-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="cart"
        options={{
          title: 'My Cart',
          tabBarLabel: 'Cart',
          tabBarBadge: totalItems > 0 ? totalItems : undefined,
          tabBarBadgeStyle: { backgroundColor: colors.warn, fontSize: 10 },
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="cart-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="deals"
        options={{ href: null }}
      />
      <Tabs.Screen
        name="auth/callback"
        options={{ href: null }}
      />
      <Tabs.Screen
        name="favorites"
        options={{
          title: 'Favorites',
          tabBarLabel: 'Favorites',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="heart-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="account"
        options={{
          title: 'Account',
          tabBarLabel: 'Account',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="person-outline" size={size} color={color} />
          ),
        }}
      />
    </Tabs>
    </>
  );
}
