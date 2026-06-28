package hue.captains.singapura.tao.http.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two TLS stages: {@link TlsConfigResolver} (resolve) then {@link TlsValidator} (validate). */
class TlsValidatorTest {

    private static byte[] keystoreBytes;

    @BeforeAll
    static void loadFixture() throws IOException {
        try (var in = TlsValidatorTest.class.getResourceAsStream("/test-keystore.jks")) {
            assertNotNull(in, "test-keystore.jks fixture must be on the test classpath");
            keystoreBytes = in.readAllBytes();
        }
    }

    private static TlsCredential.Jks credential() {
        return new TlsCredential.Jks(() -> keystoreBytes, () -> "testpass".toCharArray());
    }

    @Test
    void resolveThenValidate_validKeystore_reportsEntry() throws Exception {
        // Stage A — invoke the credential's providers to get concrete material.
        ResolvedTlsCredential resolved = new TlsConfigResolver().resolve(credential());

        // Stage B — validate the resolved material.
        TlsValidationReport report = new TlsValidator().validate(resolved);

        assertEquals("JKS", report.storeType());
        assertEquals(1, report.entries().size());
        var entry = report.entries().get(0);
        assertEquals("testcert", entry.alias());
        assertTrue(entry.keyEntry(), "fixture alias is a private-key entry");
        assertTrue(entry.keyRecoverable(), "key must be recoverable with the store password");
        assertNotNull(entry.notAfter());
        assertTrue(report.validAt(Instant.now()), "fixture cert is valid for ~10 years");
    }

    @Test
    void validate_wrongPassword_isClassified() {
        var resolved = new ResolvedTlsCredential.Jks(keystoreBytes, "wrong".toCharArray());

        var ex = assertThrows(TlsValidationException.class,
                () -> new TlsValidator().validate(resolved));
        assertEquals(TlsValidationException.Kind.WRONG_PASSWORD, ex.kind());
    }

    @Test
    void validate_corruptBytes_isClassified() {
        var resolved = new ResolvedTlsCredential.Jks(
                "definitely not a keystore".getBytes(), "x".toCharArray());

        var ex = assertThrows(TlsValidationException.class,
                () -> new TlsValidator().validate(resolved));
        assertEquals(TlsValidationException.Kind.BAD_FORMAT, ex.kind());
    }

    @Test
    void validAt_isFalseAfterExpiry() throws Exception {
        var resolved = new TlsConfigResolver().resolve(credential());
        var report = new TlsValidator().validate(resolved);

        var farFuture = Instant.now().plusSeconds(20L * 365 * 24 * 3600); // 20 years out
        assertFalse(report.validAt(farFuture));
        assertEquals(1, report.expiredAt(farFuture).size());
    }
}
