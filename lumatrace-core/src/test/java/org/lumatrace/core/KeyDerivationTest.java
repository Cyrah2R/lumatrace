package org.lumatrace.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyDerivationTest {

    @Test
    void testDeterminism() {
        long masterKey = 0xDEADBEEF;
        String user = "test-user";
        String content = "image-001";

        long seed1 = KeyDerivation.deriveSeed(masterKey, user, content);
        long seed2 = KeyDerivation.deriveSeed(masterKey, user, content);

        // Crítico: La misma entrada debe dar EXACTAMENTE la misma salida
        assertEquals(seed1, seed2, "La derivación de claves debe ser determinista");
    }

    @Test
    void testAvalancheEffect() {
        long masterKey = 0xDEADBEEF;

        long seed1 = KeyDerivation.deriveSeed(masterKey, "userA", "content");
        long seed2 = KeyDerivation.deriveSeed(masterKey, "userB", "content");

        // Un pequeño cambio en el input debe cambiar drásticamente el output
        assertNotEquals(seed1, seed2, "Diferentes usuarios deben generar semillas diferentes");
    }
}