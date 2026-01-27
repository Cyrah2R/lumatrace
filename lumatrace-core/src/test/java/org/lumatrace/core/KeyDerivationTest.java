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

        // Un pequeño cambio (una letra) debe cambiar radicalmente la semilla
        long seed1 = KeyDerivation.deriveSeed(masterKey, "userA", "content");
        long seed2 = KeyDerivation.deriveSeed(masterKey, "userB", "content");

        assertNotEquals(seed1, seed2, "Diferentes usuarios deben generar semillas diferentes");

        // Verificar que no son trivialmente similares (ej. solo +1 de diferencia)
        long diff = seed1 ^ seed2;
        assertTrue(Long.bitCount(diff) > 10, "El efecto avalancha del hash debe alterar múltiples bits");
    }
}