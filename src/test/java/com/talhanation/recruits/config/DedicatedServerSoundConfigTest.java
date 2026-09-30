package com.talhanation.recruits.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedServerSoundConfigTest {
    @Test
    void unloadedClientConfigDoesNotPreventServerSounds() {
        // This is the dedicated-server state: no client configuration is loaded.
        assertFalse(RecruitsClientConfig.CLIENT.isLoaded());

        // Reproduce the exception shown in the server's failed hire payload.
        assertThrows(IllegalStateException.class,
                () -> RecruitsClientConfig.RecruitsLookLikeVillagers.get());

        // The shared sound paths must remain usable in that same state.
        assertTrue(RecruitsClientConfig.useVillagerSounds());
    }
}
