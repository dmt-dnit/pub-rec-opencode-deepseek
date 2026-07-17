# Sprint 32 — persist auth-server's JWT signing key across restarts

**Track:** C — go-live, follow-up to Sprint 29. **Date:** 2026-07-17.

## Why this sprint exists

Found while investigating the localStorage bug, confirmed real and unfixed:
`auth-server`'s JWT signing `KeyPair` is generated **fresh and randomly on every JVM
startup** (`auth-server/src/main/java/be/dnit/authserver/config/JwtConfig.java:19-23`,
`KeyPairGenerator.generateKeyPair()`, no persisted key, no fixed seed, plus a fresh
random `keyID` via `UUID.randomUUID()` at line 29). Every backend redeploy or restart
invalidates every JWT still held by any logged-in browser, forcing everyone to log in
again — an asymmetry now made more visible by Sprint 29: user *accounts* survive
restarts, but *sessions* still don't.

This mirrors Sprint 29's pattern exactly: keep the existing crypto (RSA 2048), just
make the key persist to a file so it survives restarts, with the same environment-
variable-driven override so local dev/CI behavior is completely unchanged.

## What to change

`auth-server/src/main/java/be/dnit/authserver/config/JwtConfig.java`:

```java
package be.dnit.authserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${app.jwt.key-path:}")
    private String keyPath;

    @Bean
    public KeyPair jwtKeyPair() throws Exception {
        if (keyPath == null || keyPath.isBlank()) {
            return generateKeyPair();
        }
        Path path = Path.of(keyPath);
        if (Files.exists(path)) {
            return loadKeyPair(path);
        }
        KeyPair generated = generateKeyPair();
        saveKeyPair(generated, path);
        return generated;
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private void saveKeyPair(KeyPair keyPair, Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, keyPair.getPrivate().getEncoded());
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }

    private KeyPair loadKeyPair(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
        RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(pubSpec);
        return new KeyPair(publicKey, privateKey);
    }

    @Bean
    public JWKSet jwkSet(KeyPair keyPair) throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String keyId = fingerprint(publicKey);
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                .keyID(keyId)
                .build();
        return new JWKSet(rsaKey);
    }

    private String fingerprint(RSAPublicKey publicKey) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(publicKey.getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
    }

    @Bean
    public ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext> immutableJWKSet(JWKSet jwkSet) {
        return new ImmutableJWKSet<>(jwkSet);
    }
}
```

Key points, verify each rather than assume:
- **`app.jwt.key-path` defaults to empty** — when unset (local dev/CI, exactly as
  today), the bean falls straight back to `generateKeyPair()` with zero behavior
  change. Only the production override (`APP_JWT_KEY_PATH`, Spring's relaxed env-var
  binding, same mechanism as Sprint 29's `SPRING_DATASOURCE_URL`) activates
  persistence.
- **The `keyID` changes from a random UUID to a deterministic SHA-256 fingerprint of
  the public key** — stable across restarts as long as the underlying key is the same,
  with no extra file/state needed to track it separately. Confirm this doesn't break
  anything that assumed the old UUID format (check for any code that parses or
  validates the `kid` claim's shape — a quick grep for `getKeyID`/`"kid"` across
  `auth-server` should settle this; expected to find none, since JWKS `kid` is opaque
  to consumers by design, but verify rather than assume).
- **Private key file permissions set explicitly to `0600`** (owner read/write only) —
  this is sensitive key material; don't rely on the process's default umask.
- Uses only `java.security`/`javax.crypto` standard library APIs — no new dependency.

`deploy/systemd/env-examples/auth.env.example`: add a documented line showing the
production override, reusing the **same directory Sprint 29 already provisioned** for
the persistent H2 file (`/opt/pubrec/auth/data/`) — no new directory/permissions setup
needed on the VPS this time:
```
APP_JWT_KEY_PATH=/opt/pubrec/auth/data/jwt-signing.key
```

## Explicitly out of scope

- **No live VPS changes** — artifacts only. Applying this means adding the real
  `APP_JWT_KEY_PATH` line to the real `/etc/pubrec/auth.env` and redeploying
  `auth-server` — a manual step for Dimitri, same split as every other live-apply.
  **This redeploy will invalidate all current sessions one final time** (same
  transition cost Sprint 29 had) — after that, sessions survive every future restart.
- No change to token expiry (`access-token-expiry: 3600`), token issuance logic, or
  any other part of the auth flow — this sprint only changes how the signing key
  itself is sourced.
- No change to `SecurityConfig.java` — it already consumes the `KeyPair` bean exactly
  as before; the fix is entirely inside `JwtConfig.java`.

## Acceptance criteria (show real output, don't assert "Pass")

1. `cd shared-model && ./mvnw clean install`, then `auth-server`: `./mvnw clean verify`
   — BUILD SUCCESS.
2. Show the actual `git diff` of both changed files.
3. **Manually verify persistence works locally**, same method as Sprint 29: run
   `auth-server` locally with `APP_JWT_KEY_PATH=/tmp/sprint32-test-key.key` (disposable
   path) set as an environment variable, log in, capture the issued JWT and the
   `/oauth2/jwks` response's `kid`. Stop the process, start it again with the **same**
   env var, log in again, and confirm: (a) the new JWT's `kid` matches the first run's,
   (b) — the real proof — **the *first* JWT (issued before the restart) still
   validates successfully against `/api/auth/me` after the restart**, since the whole
   point is that sessions survive. Show the actual before/after output.

   **Important environment note from Sprint 29's verification**: the bare `java`
   command in this coordinator's WSL environment resolves to a Windows-interop shim
   that ignores `JAVA_HOME` and doesn't forward environment variables (see
   `[[feedback-wsl-java-interop-shim]]` / Sprint 29's handoff) — invoke
   `/usr/lib/jvm/java-21-openjdk-amd64/bin/java` explicitly for this local test, not
   bare `java`.
4. Confirm (grep, don't assume) that no other file in `auth-server` reads or depends on
   the JWKS `kid` claim's format.
5. `git status --short` clean after commit.

## Loop note

Reviewer: the two things worth double-checking independently are (a) the RSA public-
key-from-private-key-reconstruction logic (`RSAPublicKeySpec(modulus, publicExponent)`)
— confirm this is mathematically correct standard practice, not a shortcut that
produces a mismatched key pair, and (b) that the acceptance criteria's persistence
test actually proves a *pre-restart* token remains valid *post-restart* (the real bug
being fixed), not just that a fresh post-restart login works (which would be true even
without this fix).
