package com.scaffold.bookkeeping.security;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.system.service.SecretConfigService;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class SecretConfigServiceTest {
    private final String master = Base64.getEncoder().encodeToString(new byte[32]);

    @Test void encryptedValueIsAuthenticatedRandomizedAndRoundTrips() {
        var service = new SecretConfigService(master);
        String first = service.encrypt("test-api-value");
        String second = service.encrypt("test-api-value");
        assertNotEquals(first, second);
        assertFalse(first.contains("test-api-value"));
        assertEquals("test-api-value", service.decrypt(first));
        byte[] payload = Base64.getDecoder().decode(first.substring(3));
        payload[payload.length - 1] ^= 1;
        assertThrows(BusinessException.class, () -> service.decrypt("v1:" + Base64.getEncoder().encodeToString(payload)));
        byte[] other = new byte[32]; other[0] = 1;
        assertThrows(BusinessException.class, () -> new SecretConfigService(Base64.getEncoder().encodeToString(other)).decrypt(first));
    }

    @Test void missingMasterDoesNotPreventStartupButBlocksSecretUsage() {
        var service = new SecretConfigService("");
        assertFalse(service.ready());
        assertThrows(BusinessException.class, () -> service.encrypt("test"));
        assertThrows(BusinessException.class, () -> new SecretConfigService("malformed").encrypt("test"));
        assertThrows(BusinessException.class, () -> new SecretConfigService(Base64.getEncoder().encodeToString(new byte[16])).encrypt("test"));
    }

    @Test void keySizeFitsExistingSysConfigColumn() {
        var service = new SecretConfigService(master);
        assertTrue(service.encrypt("a".repeat(256)).length() <= 500);
        assertThrows(BusinessException.class, () -> service.encrypt("a".repeat(257)));
        assertThrows(BusinessException.class, () -> service.encrypt(" "));
    }
}
