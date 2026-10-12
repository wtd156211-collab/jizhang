package com.scaffold.bookkeeping.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.system.domain.dto.ConfigWriteRequest;
import com.scaffold.system.mapper.SystemManagementMapper;
import com.scaffold.system.service.SystemManagementService;
import com.scaffold.system.service.SystemPermissionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcrConfigProtectionTest {
    @Test
    void genericConfigCreateCannotWriteOcrReservedNamespace() {
        var mapper = mock(SystemManagementMapper.class);
        var service = new SystemManagementService(mapper, mock(PasswordEncoder.class), mock(SystemPermissionResolver.class));
        var request = new ConfigWriteRequest();
        request.setName("OCR"); request.setKey("BOOKKEEPING.OCR.API_KEY"); request.setValue("private-test-value");
        assertThrows(BusinessException.class, () -> service.createConfig(request));
        verifyNoInteractions(mapper);
    }

    @Test
    void genericConfigDtoAcceptsValueButNeverSerializesItToAuditLogs() throws Exception {
        var json = new ObjectMapper();
        var request = json.readValue("{\"key\":\"test\",\"value\":\"private-test-value\"}", ConfigWriteRequest.class);
        assertEquals("private-test-value", request.getValue());
        assertFalse(json.writeValueAsString(request).contains("private-test-value"));
    }
}
