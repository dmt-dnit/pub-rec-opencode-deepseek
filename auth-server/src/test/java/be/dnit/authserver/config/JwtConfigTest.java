package be.dnit.authserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtConfigTest {

    @Test
    void persistsKeyPairAndKeepsStableKidAcrossReloads(@TempDir Path tempDir) throws Exception {
        Path keyFile = tempDir.resolve("jwt-signing.key");

        JwtConfig firstConfig = new JwtConfig();
        ReflectionTestUtils.setField(firstConfig, "keyPath", keyFile.toString());

        KeyPair firstKeyPair = firstConfig.jwtKeyPair();
        JWKSet firstJwkSet = firstConfig.jwkSet(firstKeyPair);

        assertTrue(Files.exists(keyFile));

        JwtConfig secondConfig = new JwtConfig();
        ReflectionTestUtils.setField(secondConfig, "keyPath", keyFile.toString());

        KeyPair secondKeyPair = secondConfig.jwtKeyPair();
        JWKSet secondJwkSet = secondConfig.jwkSet(secondKeyPair);

        assertArrayEquals(firstKeyPair.getPrivate().getEncoded(), secondKeyPair.getPrivate().getEncoded());
        assertArrayEquals(firstKeyPair.getPublic().getEncoded(), secondKeyPair.getPublic().getEncoded());
        assertEquals(firstJwkSet.getKeys().get(0).getKeyID(), secondJwkSet.getKeys().get(0).getKeyID());
    }
}
