package hue.captains.singapura.tao.http.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates resolved JKS material: loads it as a {@code "JKS"} {@link KeyStore} (which
 * verifies the integrity password and format), then reports each alias with its key/cert
 * status and certificate validity window.
 */
public final class JksTlsValidator implements TlsCredentialValidator<ResolvedTlsCredential.Jks> {

    @Override
    public Class<ResolvedTlsCredential.Jks> resolvedType() {
        return ResolvedTlsCredential.Jks.class;
    }

    @Override
    public TlsValidationReport validate(ResolvedTlsCredential.Jks resolved)
            throws TlsValidationException {
        var password = resolved.password();
        var keyStore = load(resolved.keyStore(), password);
        return new TlsValidationReport("JKS", entries(keyStore, password));
    }

    private static KeyStore load(byte[] bytes, char[] password) throws TlsValidationException {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("JKS");
        } catch (KeyStoreException e) {
            throw new TlsValidationException(TlsValidationException.Kind.UNSUPPORTED,
                    "JKS keystore type is unavailable in this JVM", e);
        }
        try (var in = new ByteArrayInputStream(bytes)) {
            keyStore.load(in, password);
        } catch (IOException e) {
            if (e.getCause() instanceof UnrecoverableKeyException) {
                throw new TlsValidationException(TlsValidationException.Kind.WRONG_PASSWORD,
                        "Incorrect keystore password", e);
            }
            throw new TlsValidationException(TlsValidationException.Kind.BAD_FORMAT,
                    "Keystore could not be read (corrupt bytes or not a JKS keystore)", e);
        } catch (NoSuchAlgorithmException | CertificateException e) {
            throw new TlsValidationException(TlsValidationException.Kind.BAD_FORMAT,
                    "Keystore integrity or certificate check failed", e);
        }
        return keyStore;
    }

    private static List<TlsValidationReport.Entry> entries(KeyStore keyStore, char[] password)
            throws TlsValidationException {
        var entries = new ArrayList<TlsValidationReport.Entry>();
        try {
            for (var aliases = keyStore.aliases(); aliases.hasMoreElements(); ) {
                String alias = aliases.nextElement();
                boolean keyEntry = keyStore.isKeyEntry(alias);
                boolean keyRecoverable = keyEntry && keyRecoverable(keyStore, alias, password);

                java.time.Instant notBefore = null;
                java.time.Instant notAfter = null;
                if (keyStore.getCertificate(alias) instanceof X509Certificate x509) {
                    notBefore = x509.getNotBefore().toInstant();
                    notAfter = x509.getNotAfter().toInstant();
                }
                entries.add(new TlsValidationReport.Entry(
                        alias, keyEntry, keyRecoverable, notBefore, notAfter));
            }
        } catch (KeyStoreException e) {
            throw new TlsValidationException(TlsValidationException.Kind.BAD_FORMAT,
                    "Failed to enumerate keystore entries", e);
        }
        return List.copyOf(entries);
    }

    private static boolean keyRecoverable(KeyStore keyStore, String alias, char[] password)
            throws TlsValidationException {
        try {
            keyStore.getKey(alias, password);
            return true;
        } catch (UnrecoverableKeyException e) {
            return false;
        } catch (GeneralSecurityException e) {
            throw new TlsValidationException(TlsValidationException.Kind.BAD_FORMAT,
                    "Failed to read key for alias " + alias, e);
        }
    }
}
