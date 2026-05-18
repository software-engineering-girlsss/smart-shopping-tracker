import React, { useRef, useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  Platform,
  StyleSheet,
  ActivityIndicator,
  Modal,
  TouchableOpacity,
  type TextInput as TextInputType,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as WebBrowser from 'expo-web-browser';
import * as AuthSession from 'expo-auth-session';
import * as Crypto from 'expo-crypto';
import { colors, radius, shadow } from '../theme';
import { authApi } from '../api/client';
import { BasktLogo } from './BasktLogo';

const SUPABASE_URL = 'https://uvawswfrfepmpdlgngnj.supabase.co';
const OTP_LENGTH = 6;
const RESEND_COOLDOWN = 60;

type AuthTab = 'login' | 'register';

// ─── OTP input ────────────────────────────────────────────────────────────────

function OtpInput({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
  const refs = useRef<(TextInputType | null)[]>([]);

  const handleChange = (text: string, idx: number) => {
    const digit = text.replace(/\D/g, '').slice(-1);
    const next = [...value];
    next[idx] = digit;
    onChange(next);
    if (digit && idx < OTP_LENGTH - 1) refs.current[idx + 1]?.focus();
  };

  const handleKeyPress = (e: any, idx: number) => {
    if (e.nativeEvent.key === 'Backspace' && !value[idx] && idx > 0) {
      refs.current[idx - 1]?.focus();
    }
  };

  const handlePaste = (e: any) => {
    const text: string = e.nativeEvent?.text ?? (e as any).clipboardData?.getData?.('text') ?? '';
    const digits = text.replace(/\D/g, '').slice(0, OTP_LENGTH).split('');
    if (digits.length > 1) {
      const next = Array(OTP_LENGTH).fill('');
      digits.forEach((d, i) => { next[i] = d; });
      onChange(next);
      const focusIdx = Math.min(digits.length, OTP_LENGTH - 1);
      refs.current[focusIdx]?.focus();
    }
  };

  return (
    <View style={otpStyles.row}>
      {Array.from({ length: OTP_LENGTH }).map((_, i) => (
        <TextInput
          key={i}
          ref={r => { refs.current[i] = r; }}
          style={[otpStyles.cell, value[i] ? otpStyles.cellFilled : null]}
          value={value[i] ?? ''}
          onChangeText={t => {
            // Handle paste: if multiple digits arrive at once (web paste)
            if (t.length > 1) {
              const digits = t.replace(/\D/g, '').slice(0, OTP_LENGTH).split('');
              const next = Array(OTP_LENGTH).fill('');
              digits.forEach((d, j) => { next[j] = d; });
              onChange(next);
              refs.current[Math.min(digits.length, OTP_LENGTH - 1)]?.focus();
            } else {
              handleChange(t, i);
            }
          }}
          onKeyPress={e => handleKeyPress(e, i)}
          keyboardType="number-pad"
          maxLength={Platform.OS === 'web' ? undefined : 1}
          textAlign="center"
          selectTextOnFocus
          autoComplete="one-time-code"
        />
      ))}
    </View>
  );
}

const otpStyles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'center', gap: 10, marginVertical: 8 },
  cell: {
    width: 44,
    height: 52,
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: radius.sm,
    fontSize: 22,
    fontWeight: '700',
    color: colors.text,
    backgroundColor: colors.bg,
    textAlign: 'center',
  },
  cellFilled: {
    borderColor: colors.accent,
    backgroundColor: colors.surface,
  },
});

// ─── AuthForm ─────────────────────────────────────────────────────────────────

export function AuthForm({
  onSuccess,
  emailJustConfirmed = false,
}: {
  onSuccess: (id: string, name: string, token: string) => void;
  emailJustConfirmed?: boolean;
}) {
  const [tab, setTab] = useState<AuthTab>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // OTP verification state (registration)
  const [pendingEmail, setPendingEmail] = useState<string | null>(null);
  const [otpCode, setOtpCode] = useState<string[]>(Array(OTP_LENGTH).fill(''));
  const [otpError, setOtpError] = useState('');
  const [resendCooldown, setResendCooldown] = useState(0);
  const cooldownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Forgot password flow state
  const [forgotStep, setForgotStep] = useState<'email' | 'otp' | 'reset' | null>(null);
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotOtp, setForgotOtp] = useState<string[]>(Array(OTP_LENGTH).fill(''));
  const [forgotNewPassword, setForgotNewPassword] = useState('');
  const [forgotConfirm, setForgotConfirm] = useState('');
  const [resetSuccess, setResetSuccess] = useState(false);

  // "Email confirmed" modal (cross-tab notification)
  const [showEmailConfirmed, setShowEmailConfirmed] = useState(false);

  const passwordRef = useRef<TextInputType>(null);

  useEffect(() => {
    if (emailJustConfirmed) setShowEmailConfirmed(true);
  }, [emailJustConfirmed]);

  const startCooldown = useCallback(() => {
    setResendCooldown(RESEND_COOLDOWN);
    if (cooldownRef.current) clearInterval(cooldownRef.current);
    cooldownRef.current = setInterval(() => {
      setResendCooldown(prev => {
        if (prev <= 1) { clearInterval(cooldownRef.current!); return 0; }
        return prev - 1;
      });
    }, 1000);
  }, []);

  useEffect(() => () => { if (cooldownRef.current) clearInterval(cooldownRef.current); }, []);

  const switchTab = (next: AuthTab) => { setTab(next); setError(''); };

  // ── Google sign-in ──────────────────────────────────────────────────────────
  const handleGoogleSignIn = async () => {
    setLoading(true);
    setError('');
    try {
      const supabaseAnonKey = process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY ?? '';
      if (!supabaseAnonKey) {
        setError('Google sign-in not configured — set EXPO_PUBLIC_SUPABASE_ANON_KEY in .env.local');
        return;
      }

      const verifierBytes = await Crypto.getRandomBytesAsync(32);
      const codeVerifier = btoa(String.fromCharCode(...Array.from(verifierBytes)))
        .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
      const challengeB64 = await Crypto.digestStringAsync(
        Crypto.CryptoDigestAlgorithm.SHA256,
        codeVerifier,
        { encoding: Crypto.CryptoEncoding.BASE64 }
      );
      const codeChallenge = challengeB64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');

      if (Platform.OS === 'web') {
        localStorage.setItem('baskt_pkce_verifier', codeVerifier);
        const callbackUrl = `${window.location.origin}/auth/callback`;
        const authUrl =
          `${SUPABASE_URL}/auth/v1/authorize?provider=google` +
          `&redirect_to=${encodeURIComponent(callbackUrl)}` +
          `&code_challenge=${codeChallenge}` +
          `&code_challenge_method=S256`;

        const popup = window.open(authUrl, 'google-auth', 'width=520,height=660,scrollbars=yes');
        if (!popup) {
          localStorage.removeItem('baskt_pkce_verifier');
          setError('Popup blocked — allow popups for this site and try again');
          return;
        }

        await new Promise<void>((resolve, reject) => {
          const handler = async (event: MessageEvent) => {
            if (event.origin !== window.location.origin) return;
            if (event.data?.type === 'SUPABASE_AUTH_SUCCESS') {
              window.removeEventListener('message', handler);
              clearInterval(closedCheck);
              try {
                const { access_token, refresh_token } = event.data;
                await AsyncStorage.setItem('auth_token', access_token);
                if (refresh_token) await AsyncStorage.setItem('refresh_token', refresh_token);
                const me = await authApi.me();
                onSuccess(String(me.id), me.name, access_token);
                resolve();
              } catch (e) { reject(e); }
            } else if (event.data?.type === 'SUPABASE_AUTH_ERROR') {
              window.removeEventListener('message', handler);
              clearInterval(closedCheck);
              reject(new Error(event.data.error ?? 'Google sign-in failed'));
            }
          };
          window.addEventListener('message', handler);
          const closedCheck = setInterval(() => {
            if (popup.closed) {
              clearInterval(closedCheck);
              window.removeEventListener('message', handler);
              localStorage.removeItem('baskt_pkce_verifier');
              resolve();
            }
          }, 500);
        });
      } else {
        const redirectUri = AuthSession.makeRedirectUri({ scheme: 'baskt' });
        const authUrl =
          `${SUPABASE_URL}/auth/v1/authorize?provider=google` +
          `&redirect_to=${encodeURIComponent(redirectUri)}` +
          `&code_challenge=${codeChallenge}` +
          `&code_challenge_method=S256`;

        const result = await WebBrowser.openAuthSessionAsync(authUrl, redirectUri);
        if (result.type !== 'success') return;

        const urlParams = new URLSearchParams(result.url.split('?')[1] ?? '');
        const code = urlParams.get('code');
        if (!code) { setError('Google sign-in failed — no code received'); return; }

        const tokenRes = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=pkce`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', apikey: supabaseAnonKey },
          body: JSON.stringify({ auth_code: code, code_verifier: codeVerifier }),
        });
        if (!tokenRes.ok) {
          const err = await tokenRes.json().catch(() => ({}));
          throw new Error(err.error_description ?? err.msg ?? 'Token exchange failed');
        }
        const { access_token, refresh_token } = await tokenRes.json();
        if (!access_token) throw new Error('No token received from Google');

        await AsyncStorage.setItem('auth_token', access_token);
        if (refresh_token) await AsyncStorage.setItem('refresh_token', refresh_token);
        const me = await authApi.me();
        onSuccess(String(me.id), me.name, access_token);
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Google sign-in failed');
    } finally {
      setLoading(false);
    }
  };

  // ── Email / password submit ─────────────────────────────────────────────────
  const handleSubmit = async () => {
    if (!email.trim() || !password.trim()) { setError('Please fill in all fields'); return; }
    if (tab === 'register' && !name.trim()) { setError('Please enter your name'); return; }
    setLoading(true);
    setError('');
    try {
      const data = tab === 'login'
        ? await authApi.login(email.trim(), password)
        : await authApi.register(name.trim(), email.trim(), password);

      if ('pending_verification' in data && data.pending_verification) {
        setPendingEmail(data.email);
        setOtpCode(Array(OTP_LENGTH).fill(''));
        startCooldown();
        return;
      }

      if (data.refresh_token) await AsyncStorage.setItem('refresh_token', data.refresh_token);
      onSuccess(String((data as any).user.id), (data as any).user.name, (data as any).access_token);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  // ── OTP verify ──────────────────────────────────────────────────────────────
  const handleVerifyOtp = async () => {
    const code = otpCode.join('');
    if (code.length !== OTP_LENGTH) { setOtpError('Please enter the full 6-digit code'); return; }
    setLoading(true);
    setOtpError('');
    try {
      const data = await authApi.verifyEmail(pendingEmail!, code);
      if (data.refresh_token) await AsyncStorage.setItem('refresh_token', data.refresh_token);
      await AsyncStorage.setItem('auth_token', data.access_token);
      onSuccess(String(data.user.id), data.user.name, data.access_token);
    } catch (e: unknown) {
      setOtpError(e instanceof Error ? e.message : 'Invalid or expired code');
      setOtpCode(Array(OTP_LENGTH).fill(''));
    } finally {
      setLoading(false);
    }
  };

  // ── Resend OTP ──────────────────────────────────────────────────────────────
  const handleResend = async () => {
    if (resendCooldown > 0 || !pendingEmail) return;
    try {
      await authApi.resendCode(pendingEmail);
      startCooldown();
    } catch (e: unknown) {
      setOtpError(e instanceof Error ? e.message : 'Failed to resend code');
    }
  };

  // ── Forgot password handlers ────────────────────────────────────────────────
  const handleForgotSendCode = async () => {
    if (!forgotEmail.trim()) { setError('Please enter your email'); return; }
    setLoading(true);
    setError('');
    try {
      await authApi.forgotPassword(forgotEmail.trim());
    } catch { /* always advance — don't reveal if email exists */ }
    setForgotStep('otp');
    setForgotOtp(Array(OTP_LENGTH).fill(''));
    startCooldown();
    setLoading(false);
  };

  const handleForgotResend = async () => {
    if (resendCooldown > 0) return;
    try { await authApi.forgotPassword(forgotEmail); } catch { /* ignore */ }
    startCooldown();
  };

  const handleForgotReset = async () => {
    const code = forgotOtp.join('');
    if (code.length !== OTP_LENGTH) { setError('Please enter the full 6-digit code'); return; }
    if (forgotNewPassword.length < 8) { setError('Password must be at least 8 characters'); return; }
    if (forgotNewPassword !== forgotConfirm) { setError('Passwords do not match'); return; }
    setLoading(true);
    setError('');
    try {
      await authApi.resetPassword(forgotEmail.trim(), code, forgotNewPassword);
      setResetSuccess(true);
      setTimeout(() => {
        setForgotStep(null);
        setResetSuccess(false);
        setForgotNewPassword('');
        setForgotConfirm('');
        setForgotOtp(Array(OTP_LENGTH).fill(''));
      }, 1500);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to reset password');
    } finally {
      setLoading(false);
    }
  };

  const exitForgot = () => {
    setForgotStep(null);
    setError('');
    setForgotOtp(Array(OTP_LENGTH).fill(''));
    setForgotNewPassword('');
    setForgotConfirm('');
    setResetSuccess(false);
  };

  // ── Forgot password screens ─────────────────────────────────────────────────
  if (forgotStep === 'email') {
    return (
      <View style={styles.authCard}>
        <View style={styles.logoWrap}><BasktLogo iconSize={40} /></View>
        <TouchableOpacity style={styles.backBtn} onPress={exitForgot}>
          <Ionicons name="arrow-back" size={16} color={colors.text2} />
          <Text style={styles.backBtnText}>Back to Sign In</Text>
        </TouchableOpacity>
        <Text style={styles.otpTitle}>Reset password</Text>
        <Text style={styles.otpSubtitle}>Enter your email and we'll send you a 6-digit code</Text>
        <View style={styles.field}>
          <Text style={styles.fieldLabel}>Email</Text>
          <TextInput
            style={styles.fieldInput}
            value={forgotEmail}
            onChangeText={setForgotEmail}
            placeholder="you@example.com"
            placeholderTextColor={colors.text2}
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
          />
        </View>
        {error ? <Text style={styles.errorText}>{error}</Text> : null}
        {Platform.OS === 'web' ? (
          <button type="button" onClick={handleForgotSendCode} disabled={loading} style={{
            backgroundColor: colors.accent, borderRadius: radius.sm, padding: 14,
            border: 'none', cursor: loading ? 'wait' : 'pointer', color: '#fff',
            fontSize: 16, fontWeight: 700, marginTop: 4, opacity: loading ? 0.6 : 1,
          }}>
            {loading ? 'Sending…' : 'Send Reset Code'}
          </button>
        ) : (
          <Pressable style={({ pressed }: any) => [styles.submitBtn, (loading || pressed) && { opacity: 0.7 }]}
            onPress={handleForgotSendCode} disabled={loading}>
            {loading ? <ActivityIndicator color="#fff" size="small" /> : <Text style={styles.submitText}>Send Reset Code</Text>}
          </Pressable>
        )}
      </View>
    );
  }

  if (forgotStep === 'otp') {
    return (
      <View style={styles.authCard}>
        <View style={styles.logoWrap}><BasktLogo iconSize={40} /></View>
        <TouchableOpacity style={styles.backBtn} onPress={() => { setForgotStep('email'); setError(''); }}>
          <Ionicons name="arrow-back" size={16} color={colors.text2} />
          <Text style={styles.backBtnText}>Back</Text>
        </TouchableOpacity>
        <Text style={styles.otpTitle}>Check your email</Text>
        <Text style={styles.otpSubtitle}>
          We sent a reset code to{'\n'}
          <Text style={styles.otpEmail}>{forgotEmail}</Text>
        </Text>
        <OtpInput value={forgotOtp} onChange={setForgotOtp} />
        {error ? <Text style={styles.errorText}>{error}</Text> : null}
        {Platform.OS === 'web' ? (
          <button type="button"
            onClick={() => { setError(''); setForgotStep('reset'); }}
            disabled={forgotOtp.join('').length !== OTP_LENGTH}
            style={{
              backgroundColor: colors.accent, borderRadius: radius.sm, padding: 14,
              border: 'none', cursor: 'pointer', color: '#fff', fontSize: 16, fontWeight: 700,
              marginTop: 8, opacity: forgotOtp.join('').length !== OTP_LENGTH ? 0.5 : 1,
            }}>
            Continue
          </button>
        ) : (
          <Pressable style={({ pressed }: any) => [styles.submitBtn,
            (forgotOtp.join('').length !== OTP_LENGTH || pressed) && { opacity: 0.5 }]}
            onPress={() => { setError(''); setForgotStep('reset'); }}
            disabled={forgotOtp.join('').length !== OTP_LENGTH}>
            <Text style={styles.submitText}>Continue</Text>
          </Pressable>
        )}
        <View style={styles.resendRow}>
          <Text style={styles.resendHint}>Didn't receive the code?</Text>
          {Platform.OS === 'web' ? (
            <button type="button" onClick={handleForgotResend} disabled={resendCooldown > 0} style={{
              background: 'transparent', border: 'none',
              cursor: resendCooldown > 0 ? 'default' : 'pointer',
              color: resendCooldown > 0 ? colors.text2 : colors.accent,
              fontSize: 13, fontWeight: 700, padding: 4,
            }}>
              {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : 'Resend'}
            </button>
          ) : (
            <Text onPress={handleForgotResend}
              style={[styles.resendLink, resendCooldown > 0 && { color: colors.text2 }]}>
              {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : 'Resend'}
            </Text>
          )}
        </View>
      </View>
    );
  }

  if (forgotStep === 'reset') {
    return (
      <View style={styles.authCard}>
        <View style={styles.logoWrap}><BasktLogo iconSize={40} /></View>
        <TouchableOpacity style={styles.backBtn} onPress={() => { setForgotStep('otp'); setError(''); }}>
          <Ionicons name="arrow-back" size={16} color={colors.text2} />
          <Text style={styles.backBtnText}>Back</Text>
        </TouchableOpacity>
        <Text style={styles.otpTitle}>Set new password</Text>
        <Text style={styles.otpSubtitle}>Choose a new password for your account</Text>
        <View style={styles.field}>
          <Text style={styles.fieldLabel}>New Password</Text>
          <TextInput
            style={styles.fieldInput}
            value={forgotNewPassword}
            onChangeText={setForgotNewPassword}
            placeholder="Min. 8 characters"
            placeholderTextColor={colors.text2}
            secureTextEntry
          />
        </View>
        <View style={styles.field}>
          <Text style={styles.fieldLabel}>Confirm Password</Text>
          <TextInput
            style={styles.fieldInput}
            value={forgotConfirm}
            onChangeText={setForgotConfirm}
            placeholder="Repeat new password"
            placeholderTextColor={colors.text2}
            secureTextEntry
          />
        </View>
        {error ? <Text style={styles.errorText}>{error}</Text> : null}
        {resetSuccess ? <Text style={styles.successText}>Password reset! Redirecting…</Text> : null}
        {Platform.OS === 'web' ? (
          <button type="button" onClick={handleForgotReset} disabled={loading || resetSuccess} style={{
            backgroundColor: colors.accent, borderRadius: radius.sm, padding: 14,
            border: 'none', cursor: loading ? 'wait' : 'pointer', color: '#fff',
            fontSize: 16, fontWeight: 700, marginTop: 4, opacity: (loading || resetSuccess) ? 0.6 : 1,
          }}>
            {loading ? 'Resetting…' : resetSuccess ? 'Done!' : 'Reset Password'}
          </button>
        ) : (
          <Pressable style={({ pressed }: any) => [styles.submitBtn, (loading || pressed || resetSuccess) && { opacity: 0.7 }]}
            onPress={handleForgotReset} disabled={loading || resetSuccess}>
            {loading ? <ActivityIndicator color="#fff" size="small" />
              : <Text style={styles.submitText}>{resetSuccess ? 'Done!' : 'Reset Password'}</Text>}
          </Pressable>
        )}
      </View>
    );
  }

  // ── OTP screen ──────────────────────────────────────────────────────────────
  if (pendingEmail) {
    return (
      <View style={styles.authCard}>
        <View style={styles.logoWrap}><BasktLogo iconSize={40} /></View>
        <TouchableOpacity style={styles.backBtn} onPress={() => { setPendingEmail(null); setOtpError(''); }}>
          <Ionicons name="arrow-back" size={16} color={colors.text2} />
          <Text style={styles.backBtnText}>Back</Text>
        </TouchableOpacity>

        <Text style={styles.otpTitle}>Check your email</Text>
        <Text style={styles.otpSubtitle}>
          If an account with this email does not exist, you will receive a code by email{'\n'}
          <Text style={styles.otpEmail}>{pendingEmail}</Text>
        </Text>

        <OtpInput value={otpCode} onChange={setOtpCode} />

        {otpError ? <Text style={styles.errorText}>{otpError}</Text> : null}

        {Platform.OS === 'web' ? (
          <button
            type="button"
            onClick={handleVerifyOtp}
            disabled={loading || otpCode.join('').length !== OTP_LENGTH}
            style={{
              backgroundColor: colors.accent,
              borderRadius: radius.sm,
              padding: 14,
              border: 'none',
              cursor: loading ? 'wait' : 'pointer',
              color: '#fff',
              fontSize: 16,
              fontWeight: 700,
              marginTop: 8,
              opacity: (loading || otpCode.join('').length !== OTP_LENGTH) ? 0.5 : 1,
            }}
          >
            {loading ? 'Verifying…' : 'Verify email'}
          </button>
        ) : (
          <Pressable
            style={({ pressed }: any) => [
              styles.submitBtn,
              (loading || otpCode.join('').length !== OTP_LENGTH || pressed) && { opacity: 0.5 },
            ]}
            onPress={handleVerifyOtp}
            disabled={loading || otpCode.join('').length !== OTP_LENGTH}
          >
            {loading
              ? <ActivityIndicator color="#fff" size="small" />
              : <Text style={styles.submitText}>Verify email</Text>
            }
          </Pressable>
        )}

        <View style={styles.resendRow}>
          <Text style={styles.resendHint}>Didn't receive the code?</Text>
          {Platform.OS === 'web' ? (
            <button
              type="button"
              onClick={handleResend}
              disabled={resendCooldown > 0}
              style={{
                background: 'transparent', border: 'none',
                cursor: resendCooldown > 0 ? 'default' : 'pointer',
                color: resendCooldown > 0 ? colors.text2 : colors.accent,
                fontSize: 13, fontWeight: 700, padding: 4,
              }}
            >
              {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : 'Resend'}
            </button>
          ) : (
            <Text
              onPress={handleResend}
              style={[styles.resendLink, resendCooldown > 0 && { color: colors.text2 }]}
            >
              {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : 'Resend'}
            </Text>
          )}
        </View>
      </View>
    );
  }

  // ── Login / Register screen ─────────────────────────────────────────────────
  return (
    <View style={styles.authCard}>
      <View style={styles.logoWrap}><BasktLogo iconSize={80} layout="stacked" /></View>
      {tab === 'register' && (
        <Text style={styles.authSubtitle}>Create your account</Text>
      )}

      {tab === 'register' && (
        <View style={styles.field}>
          <Text style={styles.fieldLabel}>Name</Text>
          <TextInput
            style={styles.fieldInput}
            placeholder="Your name"
            placeholderTextColor={colors.text2}
            value={name}
            onChangeText={setName}
            autoCapitalize="words"
          />
        </View>
      )}

      <View style={styles.field}>
        <Text style={styles.fieldLabel}>Email</Text>
        <TextInput
          style={styles.fieldInput}
          placeholder="you@example.com"
          placeholderTextColor={colors.text2}
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="next"
          onSubmitEditing={() => passwordRef.current?.focus()}
          blurOnSubmit={false}
        />
      </View>

      <View style={styles.field}>
        <Text style={styles.fieldLabel}>Password</Text>
        <TextInput
          ref={passwordRef}
          style={styles.fieldInput}
          placeholder="••••••••"
          placeholderTextColor={colors.text2}
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          returnKeyType="go"
          onSubmitEditing={handleSubmit}
        />
      </View>

      {tab === 'login' && (
        Platform.OS === 'web' ? (
          <button type="button" onClick={() => { setForgotStep('email'); setForgotEmail(email); setError(''); }}
            style={{
              background: 'transparent', border: 'none', cursor: 'pointer',
              color: colors.accent, fontSize: 13, fontWeight: 600,
              alignSelf: 'flex-end', marginBottom: 4, marginTop: -4, padding: 0,
              display: 'block', textAlign: 'right',
            } as React.CSSProperties}>
            Forgot password?
          </button>
        ) : (
          <TouchableOpacity style={styles.forgotLink}
            onPress={() => { setForgotStep('email'); setForgotEmail(email); setError(''); }}>
            <Text style={styles.forgotLinkText}>Forgot password?</Text>
          </TouchableOpacity>
        )
      )}

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      {Platform.OS === 'web' ? (
        <button
          type="button"
          onClick={handleSubmit}
          disabled={loading}
          style={{
            backgroundColor: colors.accent,
            borderRadius: radius.sm,
            padding: 14,
            border: 'none',
            cursor: loading ? 'wait' : 'pointer',
            color: '#fff',
            fontSize: 16,
            fontWeight: 700,
            marginTop: 4,
            opacity: loading ? 0.6 : 1,
          }}
        >
          {loading ? 'Please wait…' : tab === 'login' ? 'Sign In' : 'Create Account'}
        </button>
      ) : (
        <Pressable
          style={({ pressed }: any) => [
            styles.submitBtn,
            (loading || pressed) && { opacity: 0.7 },
          ]}
          onPress={handleSubmit}
          disabled={loading}
          accessibilityRole="button"
        >
          {loading
            ? <ActivityIndicator color="#fff" size="small" />
            : <Text style={styles.submitText}>{tab === 'login' ? 'Sign In' : 'Create Account'}</Text>
          }
        </Pressable>
      )}

      <View style={{ height: 12 }} />

      {Platform.OS === 'web' ? (
        <button
          type="button"
          onClick={handleGoogleSignIn}
          disabled={loading}
          style={{
            backgroundColor: '#fff',
            borderRadius: radius.sm,
            padding: 13,
            border: `1.5px solid ${colors.border}`,
            cursor: loading ? 'wait' : 'pointer',
            color: colors.text,
            fontSize: 15,
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            opacity: loading ? 0.6 : 1,
          } as React.CSSProperties}
        >
          <span style={{ color: '#4285F4', fontWeight: 800, fontSize: 16 }}>G</span>
          Continue with Google
        </button>
      ) : (
        <Pressable
          style={({ pressed }: any) => [
            styles.googleBtn,
            (loading || pressed) && { opacity: 0.7 },
          ]}
          onPress={handleGoogleSignIn}
          disabled={loading}
          accessibilityRole="button"
        >
          <Ionicons name="logo-google" size={18} color="#4285F4" />
          <Text style={styles.googleBtnText}>Continue with Google</Text>
        </Pressable>
      )}

      <View style={styles.toggleRow}>
        <Text style={styles.toggleHint}>
          {tab === 'login' ? "Don't have an account?" : 'Already have an account?'}
        </Text>
        {Platform.OS === 'web' ? (
          <button
            type="button"
            onClick={() => switchTab(tab === 'login' ? 'register' : 'login')}
            style={{
              background: 'transparent', border: 'none', cursor: 'pointer',
              color: colors.accent, fontSize: 13, fontWeight: 700, padding: 4,
            }}
          >
            {tab === 'login' ? 'Create one' : 'Sign in'}
          </button>
        ) : (
          <Text
            onPress={() => switchTab(tab === 'login' ? 'register' : 'login')}
            style={styles.toggleLink}
            accessibilityRole="link"
          >
            {tab === 'login' ? 'Create one' : 'Sign in'}
          </Text>
        )}
      </View>

      {/* "Email confirmed" popup (cross-tab notification) */}
      <Modal visible={showEmailConfirmed} transparent animationType="fade" onRequestClose={() => setShowEmailConfirmed(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Ionicons name="checkmark-circle" size={40} color="#16A34A" style={{ marginBottom: 12 }} />
            <Text style={styles.modalTitle}>Email confirmed!</Text>
            <Text style={styles.modalBody}>
              Your email address has been verified.{'\n'}You can now sign in to your account.
            </Text>
            <TouchableOpacity style={styles.modalBtn} onPress={() => setShowEmailConfirmed(false)}>
              <Text style={styles.modalBtnText}>Sign in</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  authCard: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: 28,
    ...shadow.lg,
  },
  logoWrap: {
    alignItems: 'center',
    marginBottom: 4,
  },
  authTagline: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.text2,
    textAlign: 'center',
    marginBottom: 2,
    letterSpacing: 0.1,
  },
  authSubtitle: {
    fontSize: 14,
    color: colors.text2,
    textAlign: 'center',
    marginBottom: 24,
    fontWeight: '500',
  },
  field: { marginBottom: 14 },
  fieldLabel: { fontSize: 13, fontWeight: '500', color: colors.text2, marginBottom: 6 },
  fieldInput: {
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: radius.sm,
    padding: 12,
    fontSize: 15,
    color: colors.text,
    backgroundColor: colors.bg,
  },
  errorText: {
    color: colors.warn,
    fontSize: 13,
    marginBottom: 10,
    fontWeight: '500',
  },
  submitBtn: {
    backgroundColor: colors.accent,
    borderRadius: radius.sm,
    padding: 14,
    alignItems: 'center',
    marginTop: 4,
  },
  submitText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  dividerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginTop: 16,
    marginBottom: 12,
  },
  dividerLine: { flex: 1, height: 1, backgroundColor: colors.border },
  dividerText: { fontSize: 12, color: colors.text2, fontWeight: '500' },
  googleBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: colors.surface,
    borderRadius: radius.sm,
    padding: 13,
    borderWidth: 1.5,
    borderColor: colors.border,
  },
  googleBtnText: { fontSize: 15, fontWeight: '600', color: colors.text },
  toggleRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
    marginTop: 20,
  },
  toggleHint: { fontSize: 13, color: colors.text2 },
  toggleLink: { fontSize: 13, fontWeight: '700', color: colors.accent },

  // OTP screen
  backBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, marginBottom: 16 },
  backBtnText: { fontSize: 13, color: colors.text2 },
  otpTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: colors.text,
    textAlign: 'center',
    marginBottom: 8,
  },
  otpSubtitle: {
    fontSize: 14,
    color: colors.text2,
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: 16,
  },
  otpEmail: { fontWeight: '700', color: colors.text },
  resendRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
    marginTop: 20,
  },
  resendHint: { fontSize: 13, color: colors.text2 },
  resendLink: { fontSize: 13, fontWeight: '700', color: colors.accent },
  forgotLink: { alignSelf: 'flex-end', marginBottom: 4, marginTop: -4 },
  forgotLinkText: { fontSize: 13, fontWeight: '600', color: colors.accent },
  successText: { fontSize: 13, color: '#2D6A4F', fontWeight: '600', marginBottom: 8, textAlign: 'center' },

  // "Email confirmed" modal
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.45)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  modalCard: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: 28,
    alignItems: 'center',
    width: '100%',
    maxWidth: 340,
    ...shadow.lg,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: '800',
    color: colors.text,
    textAlign: 'center',
    marginBottom: 12,
  },
  modalBody: {
    fontSize: 14,
    color: colors.text2,
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: 24,
  },
  modalBtn: {
    backgroundColor: colors.accent,
    borderRadius: radius.sm,
    paddingVertical: 13,
    paddingHorizontal: 32,
  },
  modalBtnText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
