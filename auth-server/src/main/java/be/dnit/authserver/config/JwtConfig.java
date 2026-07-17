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
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
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
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, keyPair.getPrivate().getEncoded());
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX file systems (for example Windows dev boxes) do not support chmod-style permissions.
        }
    }

    private KeyPair loadKeyPair(Path path) throws Exception {
        byte[] encodedPrivateKey = Files.readAllBytes(path);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encodedPrivateKey));
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
        return new KeyPair(publicKey, privateKey);
    }

    @Bean
    public JWKSet jwkSet(KeyPair keyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(fingerprint(publicKey))
                .build();
        return new JWKSet(rsaKey);
    }

    private String fingerprint(RSAPublicKey publicKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(publicKey.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive JWT key fingerprint", e);
        }
    }

    @Bean
    public ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext> immutableJWKSet(JWKSet jwkSet) {
        return new ImmutableJWKSet<>(jwkSet);
    }
}
