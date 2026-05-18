// app/account.tsx
import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Modal,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { colors, radius, shadow } from '../src/theme';
import { useStore } from '../src/store';
import { authApi, userApi, PicnicNeeds2FAResponse } from '../src/api/client';
import { AuthForm } from '../src/components/AuthForm';

type SettingsModal = 'privacy' | 'edit-profile' | 'change-password' | 'picnic' | null;

// Settings Modals
function EditProfileModal({
  visible, currentName, onClose, onSave,
}: {
  visible: boolean;
  currentName: string;
  onClose: () => void;
  onSave: (name: string) => Promise<void>;
}) {
  const [name, setName] = useState(currentName);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  React.useEffect(() => {
    if (visible) { setName(currentName); setError(null); }
  }, [visible, currentName]);

  const handleSave = async () => {
    const trimmed = name.trim();
    if (!trimmed) { setError('Name cannot be empty'); return; }
    setLoading(true);
    setError(null);
    try {
      await onSave(trimmed);
      onClose();
    } catch (e: any) {
      setError(e?.message ?? 'Failed to update name');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide">
      <SafeAreaView style={styles.modalContainer} edges={['top', 'bottom']}>
        <View style={styles.modalHeader}>
          <Text style={styles.modalTitle}>Edit Profile</Text>
          <TouchableOpacity onPress={onClose}>
            <Ionicons name="close" size={24} color={colors.text} />
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={styles.modalContent} keyboardShouldPersistTaps="handled">
          <View style={styles.field}>
            <Text style={styles.fieldLabel}>Display Name</Text>
            <TextInput
              style={styles.fieldInput}
              value={name}
              onChangeText={setName}
              placeholder="Your name"
              placeholderTextColor={colors.text2}
              autoCapitalize="words"
            />
          </View>
          {error ? <Text style={styles.formError}>{error}</Text> : null}
          <TouchableOpacity
            style={[styles.modalButton, loading && { opacity: 0.6 }]}
            onPress={handleSave}
            disabled={loading}
          >
            <Text style={styles.modalButtonText}>{loading ? 'Saving…' : 'Save'}</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
}

function ChangePasswordModal({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  React.useEffect(() => {
    if (visible) { setNewPassword(''); setConfirmPassword(''); setError(null); setSuccess(false); }
  }, [visible]);

  const handleSave = async () => {
    if (newPassword.length < 8) { setError('Password must be at least 8 characters'); return; }
    if (newPassword !== confirmPassword) { setError('Passwords do not match'); return; }
    setLoading(true);
    setError(null);
    try {
      await authApi.changePassword(newPassword);
      setSuccess(true);
      setTimeout(onClose, 1200);
    } catch (e: any) {
      setError(e?.message ?? 'Failed to change password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide">
      <SafeAreaView style={styles.modalContainer} edges={['top', 'bottom']}>
        <View style={styles.modalHeader}>
          <Text style={styles.modalTitle}>Change Password</Text>
          <TouchableOpacity onPress={onClose}>
            <Ionicons name="close" size={24} color={colors.text} />
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={styles.modalContent} keyboardShouldPersistTaps="handled">
          <View style={styles.field}>
            <Text style={styles.fieldLabel}>New Password</Text>
            <TextInput
              style={styles.fieldInput}
              value={newPassword}
              onChangeText={setNewPassword}
              placeholder="Min. 8 characters"
              placeholderTextColor={colors.text2}
              secureTextEntry
            />
          </View>
          <View style={styles.field}>
            <Text style={styles.fieldLabel}>Confirm Password</Text>
            <TextInput
              style={styles.fieldInput}
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              placeholder="Repeat new password"
              placeholderTextColor={colors.text2}
              secureTextEntry
            />
          </View>
          {error ? <Text style={styles.formError}>{error}</Text> : null}
          {success ? <Text style={styles.formSuccess}>Password changed!</Text> : null}
          <TouchableOpacity
            style={[styles.modalButton, loading && { opacity: 0.6 }]}
            onPress={handleSave}
            disabled={loading}
          >
            <Text style={styles.modalButtonText}>{loading ? 'Saving…' : 'Change Password'}</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
}

function PrivacyModal({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  return (
    <Modal visible={visible} transparent={true} animationType="slide">
      <SafeAreaView style={styles.modalContainer} edges={['top', 'bottom']}>
        <View style={styles.modalHeader}>
          <Text style={styles.modalTitle}>Privacy & Data</Text>
          <TouchableOpacity onPress={onClose}>
            <Ionicons name="close" size={24} color={colors.text} />
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={styles.modalContent}>
          <Text style={styles.privacyText}>
            Your data is encrypted and stored securely. We do not share your personal information with third parties.
          </Text>
          <Text style={styles.privacySubheading}>What we collect:</Text>
          <Text style={styles.privacyText}>
            • Shopping history{'\n'}
            • Preferred stores{'\n'}
            • Dietary preferences{'\n'}
            • Notification settings
          </Text>
          <Text style={styles.privacySubheading}>Your rights:</Text>
          <Text style={styles.privacyText}>
            • Access your data{'\n'}
            • Request deletion{'\n'}
            • Update preferences
          </Text>
          <TouchableOpacity style={styles.modalButton} onPress={onClose}>
            <Text style={styles.modalButtonText}>I Understand</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
}

function PicnicModal({
  visible,
  connected,
  currentEmail,
  currentZip,
  onClose,
  onConnect,
  onDisconnect,
}: {
  visible: boolean;
  connected: boolean;
  currentEmail: string | null;
  currentZip: string | null;
  onClose: () => void;
  onConnect: (account: { email: string; zip_code?: string }) => void;
  onDisconnect: () => Promise<void>;
}) {
  const [step, setStep] = useState<'connect' | 'otp'>('connect');
  const [email, setEmail] = useState(currentEmail ?? '');
  const [password, setPassword] = useState('');
  const [zip, setZip] = useState(currentZip ?? '');
  const [otp, setOtp] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  React.useEffect(() => {
    if (visible) {
      setStep('connect');
      setEmail(currentEmail ?? '');
      setPassword('');
      setZip(currentZip ?? '');
      setOtp('');
      setError(null);
    }
  }, [visible, currentEmail, currentZip]);

  const handleConnect = async () => {
    const trimEmail = email.trim();
    const trimZip = zip.trim();
    if (!trimEmail || !trimEmail.includes('@')) { setError('Enter a valid Picnic email'); return; }
    if (password.length < 6) { setError('Password must be at least 6 characters'); return; }
    setLoading(true);
    setError(null);
    try {
      const result = await userApi.connectPicnic(trimEmail, password, trimZip || undefined);
      if ('needs_2fa' in result && result.needs_2fa) {
        setStep('otp');
      } else {
        const account = result as import('../src/api/client').ConnectedAccount;
        onConnect({ email: account.email, zip_code: account.zip_code ?? undefined });
        onClose();
      }
    } catch (e: any) {
      setError(e?.message ?? 'Could not connect Picnic account');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async () => {
    const trimOtp = otp.trim();
    if (!trimOtp) { setError('Enter the verification code'); return; }
    setLoading(true);
    setError(null);
    try {
      const account = await userApi.verify2fa(trimOtp);
      onConnect({ email: account.email, zip_code: account.zip_code ?? undefined });
      onClose();
    } catch (e: any) {
      setError(e?.message ?? 'Invalid code — please try again');
    } finally {
      setLoading(false);
    }
  };

  const handleDisconnect = async () => {
    setLoading(true);
    setError(null);
    try {
      await onDisconnect();
      onClose();
    } catch (e: any) {
      setError(e?.message ?? 'Could not disconnect');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide">
      <SafeAreaView style={styles.modalContainer} edges={['top', 'bottom']}>
        <View style={styles.modalHeader}>
          <Text style={styles.modalTitle}>Picnic Account</Text>
          <TouchableOpacity onPress={onClose}>
            <Ionicons name="close" size={24} color={colors.text} />
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={styles.modalContent} keyboardShouldPersistTaps="handled">
          {connected ? (
            <>
              <View style={styles.picnicConnectedCard}>
                <Ionicons name="checkmark-circle" size={22} color="#2D6A4F" />
                <View style={{ flex: 1 }}>
                  <Text style={styles.picnicConnectedEmail}>{currentEmail}</Text>
                  {currentZip ? (
                    <Text style={styles.picnicConnectedZip}>Delivery area: {currentZip}</Text>
                  ) : (
                    <Text style={styles.picnicConnectedZip}>No delivery area set</Text>
                  )}
                </View>
              </View>
              <Text style={styles.picnicDisclosure}>
                Your encrypted Picnic credentials are stored on our servers and used only to fetch prices and enable cart import on your behalf.
              </Text>
              <TouchableOpacity
                style={[styles.picnicDisconnectBtn, loading && { opacity: 0.6 }]}
                onPress={handleDisconnect}
                disabled={loading}
              >
                <Text style={styles.picnicDisconnectText}>{loading ? 'Disconnecting…' : 'Disconnect Picnic'}</Text>
              </TouchableOpacity>
              {error ? <Text style={[styles.formError, { marginTop: 12 }]}>{error}</Text> : null}
            </>
          ) : step === 'otp' ? (
            <>
              <View style={styles.picnicDisclosureCard}>
                <Ionicons name="mail-outline" size={18} color={colors.text2} style={{ marginTop: 2 }} />
                <Text style={styles.picnicDisclosureText}>
                  Picnic sent a verification code to your registered 2FA channel. Enter it below to complete the connection.
                </Text>
              </View>
              <View style={styles.field}>
                <Text style={styles.fieldLabel}>Verification Code</Text>
                <TextInput
                  style={styles.fieldInput}
                  value={otp}
                  onChangeText={setOtp}
                  placeholder="e.g. 123456"
                  placeholderTextColor={colors.text2}
                  keyboardType="number-pad"
                  autoCapitalize="none"
                  autoCorrect={false}
                  maxLength={8}
                />
              </View>
              {error ? <Text style={styles.formError}>{error}</Text> : null}
              <TouchableOpacity
                style={[styles.modalButton, loading && { opacity: 0.6 }]}
                onPress={handleVerifyOtp}
                disabled={loading}
              >
                <Text style={styles.modalButtonText}>{loading ? 'Verifying…' : 'Verify Code'}</Text>
              </TouchableOpacity>
              <TouchableOpacity style={{ marginTop: 12, alignItems: 'center' }} onPress={() => { setStep('connect'); setError(null); }}>
                <Text style={{ color: colors.text2, fontSize: 14 }}>Back</Text>
              </TouchableOpacity>
            </>
          ) : (
            <>
              <View style={styles.picnicDisclosureCard}>
                <Ionicons name="information-circle-outline" size={18} color={colors.text2} style={{ marginTop: 2 }} />
                <Text style={styles.picnicDisclosureText}>
                  Your Picnic email and password are encrypted (AES-256) and stored on our servers. They are used solely to fetch personalised prices and enable cart import on your behalf. You can disconnect at any time. We never share your credentials.
                </Text>
              </View>
              <View style={styles.field}>
                <Text style={styles.fieldLabel}>Picnic Email</Text>
                <TextInput
                  style={styles.fieldInput}
                  value={email}
                  onChangeText={setEmail}
                  placeholder="you@example.com"
                  placeholderTextColor={colors.text2}
                  keyboardType="email-address"
                  autoCapitalize="none"
                  autoCorrect={false}
                />
              </View>
              <View style={styles.field}>
                <Text style={styles.fieldLabel}>Picnic Password</Text>
                <TextInput
                  style={styles.fieldInput}
                  value={password}
                  onChangeText={setPassword}
                  placeholder="Your Picnic password"
                  placeholderTextColor={colors.text2}
                  secureTextEntry
                  autoCapitalize="none"
                />
              </View>
              <View style={styles.field}>
                <Text style={styles.fieldLabel}>Delivery ZIP Code (optional)</Text>
                <TextInput
                  style={styles.fieldInput}
                  value={zip}
                  onChangeText={setZip}
                  placeholder="e.g. 10115"
                  placeholderTextColor={colors.text2}
                  keyboardType="numeric"
                  maxLength={10}
                />
              </View>
              {error ? <Text style={styles.formError}>{error}</Text> : null}
              <TouchableOpacity
                style={[styles.modalButton, loading && { opacity: 0.6 }]}
                onPress={handleConnect}
                disabled={loading}
              >
                <Text style={styles.modalButtonText}>{loading ? 'Connecting…' : 'Connect Picnic'}</Text>
              </TouchableOpacity>
            </>
          )}
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
}

export default function AccountScreen() {
  const { userId, userName, userEmail, setUser, clearUser, clearCart,
    picnicConnected, picnicEmail, zipCode, setPicnicConnection, clearPicnicConnection } = useStore();
  const [activeModal, setActiveModal] = useState<SettingsModal>(null);
  const [logoutConfirm, setLogoutConfirm] = useState(false);

  const handleConnectPicnic = (account: { email: string; zip_code?: string }) => {
    setPicnicConnection(account.email, account.zip_code);
  };

  const handleDisconnectPicnic = async () => {
    await userApi.disconnectPicnic();
    clearPicnicConnection();
  };

  const handleSaveName = async (name: string) => {
    await authApi.updateProfile(name);
    if (userId) setUser(userId, name, userEmail ?? undefined);
    // The JWT in AsyncStorage still has the old name claim — refresh it so the
    // new name persists across reloads (Supabase encodes metadata in the JWT).
    const storedRefresh = await AsyncStorage.getItem('refresh_token');
    if (storedRefresh) {
      try {
        const tokens = await authApi.refresh(storedRefresh);
        await AsyncStorage.setItem('auth_token', tokens.access_token);
        if (tokens.refresh_token) await AsyncStorage.setItem('refresh_token', tokens.refresh_token);
      } catch { /* non-fatal — name is updated in-session */ }
    }
  };

  const handleAuthSuccess = async (id: string, name: string, token: string) => {
    await AsyncStorage.setItem('auth_token', token);
    try {
      const me = await authApi.me();
      setUser(id, name, me.email);
    } catch {
      setUser(id, name);
    }
  };

  const doLogout = async () => {
    setLogoutConfirm(false);
    try { await authApi.logout(); } catch { /* token may already be invalid */ }
    await AsyncStorage.removeItem('auth_token');
    await AsyncStorage.removeItem('refresh_token');
    clearUser();
    clearCart();
  };

  const handleLogout = () => setLogoutConfirm(true);

  if (!userId) {
    return (
      <SafeAreaView style={styles.container} edges={['bottom']}>
        <ScrollView contentContainerStyle={styles.authScroll} keyboardShouldPersistTaps="handled">
          <AuthForm onSuccess={handleAuthSuccess} />
        </ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        {/* Profile header */}
        <View style={styles.profileCard}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>{userName?.charAt(0).toUpperCase() ?? '?'}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.profileName}>{userName}</Text>
            <Text style={styles.profileId}>{userEmail ?? `ID: ${userId.slice(0, 8)}…`}</Text>
          </View>
          <TouchableOpacity style={styles.logoutBtn} onPress={handleLogout}>
            <Ionicons name="log-out-outline" size={18} color={colors.warn} />
            <Text style={styles.logoutText}>Sign Out</Text>
          </TouchableOpacity>
        </View>

        {/* Account rows */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Account</Text>
          <TouchableOpacity
            style={styles.settingsRow}
            onPress={() => setActiveModal('edit-profile')}
          >
            <Ionicons name="person-outline" size={20} color={colors.text2} />
            <Text style={styles.settingsLabel}>Edit Profile</Text>
            <Ionicons name="chevron-forward" size={16} color={colors.border} />
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.settingsRow, { borderBottomWidth: 0 }]}
            onPress={() => setActiveModal('change-password')}
          >
            <Ionicons name="lock-closed-outline" size={20} color={colors.text2} />
            <Text style={styles.settingsLabel}>Change Password</Text>
            <Ionicons name="chevron-forward" size={16} color={colors.border} />
          </TouchableOpacity>
        </View>

        {/* Personalization */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Personalization</Text>
          <TouchableOpacity
            style={[styles.settingsRow, { borderBottomWidth: 0 }]}
            onPress={() => setActiveModal('picnic')}
          >
            <Ionicons name="cart-outline" size={20} color={colors.text2} />
            <View style={{ flex: 1 }}>
              <Text style={styles.settingsLabel}>Picnic Account</Text>
              {picnicConnected && picnicEmail ? (
                <Text style={styles.settingsSubLabel}>{picnicEmail}</Text>
              ) : (
                <Text style={styles.settingsSubLabel}>Not connected</Text>
              )}
            </View>
            <Ionicons
              name={picnicConnected ? 'checkmark-circle' : 'chevron-forward'}
              size={picnicConnected ? 18 : 16}
              color={picnicConnected ? '#2D6A4F' : colors.border}
            />
          </TouchableOpacity>
        </View>

        {/* Settings rows */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Settings</Text>
          <TouchableOpacity
            style={[styles.settingsRow, { borderBottomWidth: 0 }]}
            onPress={() => setActiveModal('privacy')}
          >
            <Ionicons name="shield-checkmark-outline" size={20} color={colors.text2} />
            <Text style={styles.settingsLabel}>Privacy & Data</Text>
            <Ionicons name="chevron-forward" size={16} color={colors.border} />
          </TouchableOpacity>
        </View>

        <Text style={styles.version}>From CUB with love ฅ^•ﻌ•^ฅ</Text>
      </ScrollView>

      {/* Modals */}
      <PicnicModal
        visible={activeModal === 'picnic'}
        connected={picnicConnected}
        currentEmail={picnicEmail}
        currentZip={zipCode}
        onClose={() => setActiveModal(null)}
        onConnect={handleConnectPicnic}
        onDisconnect={handleDisconnectPicnic}
      />
      <EditProfileModal
        visible={activeModal === 'edit-profile'}
        currentName={userName ?? ''}
        onClose={() => setActiveModal(null)}
        onSave={handleSaveName}
      />
      <ChangePasswordModal visible={activeModal === 'change-password'} onClose={() => setActiveModal(null)} />
      <PrivacyModal visible={activeModal === 'privacy'} onClose={() => setActiveModal(null)} />

      <Modal visible={logoutConfirm} transparent animationType="fade">
        <View style={styles.overlay}>
          <View style={styles.confirmCard}>
            <View style={styles.confirmIconWrap}>
              <Ionicons name="log-out-outline" size={30} color={colors.warn} />
            </View>
            <Text style={styles.confirmTitle}>Sign Out</Text>
            <Text style={styles.confirmMessage}>
              Are you sure you want to sign out of your account?
            </Text>
            <View style={styles.confirmButtons}>
              <TouchableOpacity style={styles.confirmCancel} onPress={() => setLogoutConfirm(false)}>
                <Text style={styles.confirmCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.confirmLogout} onPress={doLogout}>
                <Text style={styles.confirmLogoutText}>Sign Out</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  authScroll: { flexGrow: 1, justifyContent: 'center', padding: 24 },
  scroll: { padding: 16, paddingBottom: 40 },

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

  // Profile
  profileCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 16,
    marginBottom: 16,
    gap: 14,
    ...shadow.sm,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: colors.accentLight,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { fontSize: 20, fontWeight: '700', color: colors.accent },
  profileName: { fontSize: 17, fontWeight: '700', color: colors.text },
  profileId: { fontSize: 12, color: colors.text2, marginTop: 2 },
  logoutBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    padding: 8,
    borderRadius: radius.sm,
    backgroundColor: colors.warnLight,
  },
  logoutText: { fontSize: 13, fontWeight: '600', color: colors.warn },

  // Sections
  section: {
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 16,
    marginBottom: 12,
    ...shadow.sm,
  },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: colors.text, marginBottom: 12 },
  settingsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 13,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    gap: 12,
  },
  settingsLabel: { fontSize: 15, color: colors.text },
  settingsSubLabel: { fontSize: 12, color: colors.text2, marginTop: 2 },
  version: { fontSize: 12, color: colors.text2, textAlign: 'center', marginTop: 16 },

  // Picnic modal
  picnicDisclosureCard: {
    flexDirection: 'row',
    gap: 10,
    backgroundColor: colors.accentLight,
    borderRadius: radius.sm,
    padding: 12,
    marginBottom: 20,
  },
  picnicDisclosureText: {
    flex: 1,
    fontSize: 13,
    color: colors.text2,
    lineHeight: 19,
  },
  picnicDisclosure: {
    fontSize: 13,
    color: colors.text2,
    lineHeight: 19,
    marginBottom: 20,
  },
  picnicConnectedCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    backgroundColor: '#E8F5E9',
    borderRadius: radius.sm,
    padding: 14,
    marginBottom: 16,
  },
  picnicConnectedEmail: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
  },
  picnicConnectedZip: {
    fontSize: 12,
    color: colors.text2,
    marginTop: 2,
  },
  picnicDisconnectBtn: {
    borderWidth: 1.5,
    borderColor: colors.warn,
    borderRadius: radius.sm,
    padding: 14,
    alignItems: 'center',
  },
  picnicDisconnectText: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.warn,
  },
  formError: { fontSize: 13, color: colors.warn, marginBottom: 8, textAlign: 'center' },
  formSuccess: { fontSize: 13, color: '#2D6A4F', marginBottom: 8, textAlign: 'center', fontWeight: '600' },

  // Modals
  modalContainer: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: colors.text,
  },
  modalContent: {
    padding: 16,
    paddingBottom: 40,
  },
  notificationItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 14,
    marginBottom: 12,
    ...shadow.sm,
  },
  notificationLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
    marginBottom: 2,
  },
  notificationDesc: {
    fontSize: 12,
    color: colors.text2,
  },
  languageOption: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 14,
    marginBottom: 12,
    ...shadow.sm,
  },
  languageLabel: {
    fontSize: 16,
    color: colors.text,
    fontWeight: '500',
  },
  modalButton: {
    backgroundColor: colors.accent,
    borderRadius: radius.sm,
    padding: 14,
    alignItems: 'center',
    marginTop: 24,
  },
  modalButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
  privacyText: {
    fontSize: 14,
    color: colors.text,
    lineHeight: 20,
    marginBottom: 12,
  },
  privacySubheading: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
    marginTop: 12,
    marginBottom: 6,
  },

  // Logout confirmation modal
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.45)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  confirmCard: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: 28,
    width: '100%',
    maxWidth: 360,
    alignItems: 'center',
    ...shadow.lg,
  },
  confirmIconWrap: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: colors.warnLight,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  confirmTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: colors.text,
    marginBottom: 8,
  },
  confirmMessage: {
    fontSize: 15,
    color: colors.text2,
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: 24,
  },
  confirmButtons: {
    flexDirection: 'row',
    gap: 12,
    width: '100%',
  },
  confirmCancel: {
    flex: 1,
    paddingVertical: 13,
    borderRadius: radius.sm,
    borderWidth: 1.5,
    borderColor: colors.border,
    alignItems: 'center',
  },
  confirmCancelText: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text2,
  },
  confirmLogout: {
    flex: 1,
    paddingVertical: 13,
    borderRadius: radius.sm,
    backgroundColor: colors.warn,
    alignItems: 'center',
  },
  confirmLogoutText: {
    fontSize: 15,
    fontWeight: '700',
    color: '#fff',
  },
});