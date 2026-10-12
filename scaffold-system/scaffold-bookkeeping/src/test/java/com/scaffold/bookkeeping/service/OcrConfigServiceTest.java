package com.scaffold.bookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.bookkeeping.domain.dto.OcrConfigRequest;
import com.scaffold.bookkeeping.mapper.OcrConfigMapper;
import com.scaffold.bookkeeping.ocr.DisabledOcrProvider;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.system.domain.AuthUserRow;
import com.scaffold.system.mapper.AuthMapper;
import com.scaffold.system.service.SecretConfigService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OcrConfigServiceTest {
    final Map<String,String> stored = new HashMap<>();
    final OcrConfigMapper mapper = mock(OcrConfigMapper.class);
    final AuthMapper auth = mock(AuthMapper.class);
    final SecretConfigService secrets = new SecretConfigService(Base64.getEncoder().encodeToString(new byte[32]));
    OcrConfigService service;
    @BeforeEach void setup() {
        when(mapper.selectValue(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
        when(mapper.selectSettings()).thenAnswer(call -> stored.entrySet().stream()
                .map(entry -> Map.of("name", entry.getKey(), "value", entry.getValue())).toList());
        doAnswer(call -> { stored.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(mapper).upsert(anyString(), anyString(), anyString());
        AuthUserRow user = new AuthUserRow(); user.setId(1L); user.setStatus(1);
        when(auth.selectUserByUsername("admin")).thenReturn(user);
        when(auth.selectRoleCodes(1L)).thenReturn(List.of("super_admin"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "", List.of()));
        service = new OcrConfigService(mapper, secrets, auth, List.of(new DisabledOcrProvider()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void keyReplacementPreservationAndClearingNeverExposeStoredValue() throws Exception {
        var request = request("disabled", "https://ocr.example.com/recognize", "test-key-123456");
        service.save(request);
        assertFalse(stored.get("bookkeeping.ocr.api_key").contains("test-key-123456"));
        String status = new ObjectMapper().writeValueAsString(service.status());
        assertFalse(status.contains("test-key-123456")); assertFalse(status.contains("v1:"));
        assertEquals("******3456", service.status().keySuffix());
        service.save(request("disabled", "", null));
        assertEquals("test-key-123456", secrets.decrypt(stored.get("bookkeeping.ocr.api_key")));
        service.save(request("disabled", "", "replacement-9876"));
        assertEquals("******9876", service.status().keySuffix());
        var clear = request("disabled", "", null); clear.setClearApiKey(true); service.save(clear);
        assertFalse(service.status().keyConfigured());
    }
    @Test void nonAdministratorCannotReadOrWriteConfigEvenWithMenuPermissions() {
        when(auth.selectRoleCodes(1L)).thenReturn(List.of("biz_admin"));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.status()).code());
        assertThrows(BusinessException.class, () -> service.save(request("disabled", "", "test-key")));
        assertTrue(stored.isEmpty());
    }
    @Test void rejectsUnknownProviderUnsafeEndpointsAndAmbiguousKeyAction() {
        for (String url : List.of("http://ocr.example.com", "https://ocr.example.com/login", "https://ocr.example.com?key=x", "https://ocr.example.com/#x")) {
            assertThrows(BusinessException.class, () -> service.save(request("disabled", url, null)));
        }
        assertThrows(BusinessException.class, () -> service.save(request("unknown", "", null)));
        var ambiguous = request("disabled", "", "test-key"); ambiguous.setClearApiKey(true);
        assertThrows(BusinessException.class, () -> service.save(ambiguous));
        assertTrue(stored.isEmpty());
    }
    @Test void disabledProviderReportsUnavailableAndShortKeysHaveNoVisibleSuffix() {
        service.save(request("disabled", "", "abc"));
        assertEquals("******", service.status().keySuffix());
        assertFalse(service.status().recognitionAvailable());
    }
    static OcrConfigRequest request(String provider, String endpoint, String apiKey) {
        var value = new OcrConfigRequest(); value.setProvider(provider); value.setEndpoint(endpoint); value.setApiKey(apiKey);
        if (apiKey != null) value.setAccessKeyId("test-access-id-1234");
        return value;
    }

    @Test void aliyunCredentialsAreBothEncryptedAndEndpointCannotRedirectUploads() throws Exception {
        service = new OcrConfigService(mapper, secrets, auth, List.of(new DisabledOcrProvider(),
                new com.scaffold.bookkeeping.ocr.AliyunOcrProvider(mock(com.scaffold.bookkeeping.ocr.AliyunOcrTransport.class), new ObjectMapper())));
        service.save(request("aliyun", "", "test-access-secret-9876"));
        assertTrue(service.status().recognitionAvailable());
        assertEquals("test-access-id-1234", secrets.decrypt(stored.get("bookkeeping.ocr.access_key_id")));
        assertFalse(new ObjectMapper().writeValueAsString(service.status()).contains("test-access-id-1234"));
        assertThrows(BusinessException.class, () -> service.save(request("aliyun", "https://evil.example.com", null)));
    }
}
