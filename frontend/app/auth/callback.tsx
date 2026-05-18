// app/auth/callback.tsx
// OAuth popup lands here after Google auth. Exchanges the PKCE code for tokens,
// sends them to the opener via postMessage, then closes itself.
// The token never appears in the main window's URL.
import { useEffect, useState } from 'react';
import { View, Text, ActivityIndicator, Platform } from 'react-native';
import { useLocalSearchParams } from 'expo-router';

const SUPABASE_URL = 'https://uvawswfrfepmpdlgngnj.supabase.co';

export default function AuthCallbackScreen() {
  // Read via expo-router's routing state — survives React Navigation's history.replaceState
  // which strips query params from window.location before useEffect runs.
  const { code: codeParam } = useLocalSearchParams<{ code?: string }>();
  const code = Array.isArray(codeParam) ? codeParam[0] : codeParam;

  const [status, setStatus] = useState<'processing' | 'done' | 'error'>('processing');
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    if (Platform.OS !== 'web') return;

    const supabaseAnonKey = process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY ?? '';
    const codeVerifier = localStorage.getItem('baskt_pkce_verifier');

    const fail = (msg: string) => {
      setStatus('error');
      setErrorMsg(msg);
      window.opener?.postMessage({ type: 'SUPABASE_AUTH_ERROR', error: msg }, window.location.origin);
      setTimeout(() => window.close(), 2000);
    };

    if (!code) { fail('Missing authorization code'); return; }
    if (!codeVerifier) { fail('Missing PKCE verifier — try signing in again'); return; }
    if (!supabaseAnonKey) { fail('App not configured (missing anon key)'); return; }

    localStorage.removeItem('baskt_pkce_verifier');

    fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=pkce`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: supabaseAnonKey },
      body: JSON.stringify({ auth_code: code, code_verifier: codeVerifier }),
    })
      .then(r => r.json())
      .then(data => {
        if (!data.access_token) {
          fail(data.error_description ?? data.msg ?? 'Token exchange failed');
          return;
        }
        setStatus('done');
        window.opener?.postMessage(
          { type: 'SUPABASE_AUTH_SUCCESS', access_token: data.access_token, refresh_token: data.refresh_token ?? '' },
          window.location.origin
        );
        setTimeout(() => window.close(), 500);
      })
      .catch(e => fail(e.message ?? 'Network error'));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff', gap: 12 }}>
      {status === 'processing' && (
        <>
          <ActivityIndicator size="large" color="#3B82F6" />
          <Text style={{ color: '#666', fontSize: 15 }}>Completing sign-in…</Text>
        </>
      )}
      {status === 'done' && (
        <Text style={{ color: '#22c55e', fontSize: 15 }}>Signed in! Closing window…</Text>
      )}
      {status === 'error' && (
        <Text style={{ color: '#ef4444', fontSize: 15, textAlign: 'center', padding: 24 }}>
          {errorMsg}
        </Text>
      )}
    </View>
  );
}
