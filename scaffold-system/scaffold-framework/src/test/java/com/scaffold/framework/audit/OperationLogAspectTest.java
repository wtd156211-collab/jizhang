package com.scaffold.framework.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationLogAspectTest {
    @Test
    void logsFileMetadataWithoutReadingOrSerializingItsBytes() {
        AtomicReference<OperationLogEntry> recorded = new AtomicReference<>();
        OperationLogAspect aspect = new OperationLogAspect(recorded::set, new ObjectMapper());
        AspectJProxyFactory factory = new AspectJProxyFactory(new UploadTarget());
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        UploadTarget target = factory.getProxy();
        MultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "%PDF-private-receipt".getBytes()) {
            @Override public byte[] getBytes() {
                throw new IllegalStateException("不能在操作日志中读取附件");
            }
        };

        assertEquals("saved", target.upload(file));
        assertTrue(recorded.get().requestParams().contains("receipt.pdf"));
        assertFalse(recorded.get().requestParams().contains("private-receipt"));
    }

    public static class UploadTarget {
        @OperLog(module = "票据附件", action = "上传")
        public String upload(MultipartFile file) { return "saved"; }

        @OperLog(module = "配置", action = "保存")
        public String save(Object request) { return "saved"; }
    }

    @Test
    void masksNestedApiKeysAndRetainsNonSensitiveSettings() {
        AtomicReference<OperationLogEntry> recorded = new AtomicReference<>();
        AspectJProxyFactory factory = new AspectJProxyFactory(new UploadTarget());
        factory.setProxyTargetClass(true);
        factory.addAspect(new OperationLogAspect(recorded::set, new ObjectMapper()));
        UploadTarget target = factory.getProxy();

        target.save(Map.of("ocr", Map.of("api_key", "private-key", "provider", "test-provider")));

        assertFalse(recorded.get().requestParams().contains("private-key"));
        assertTrue(recorded.get().requestParams().contains("test-provider"));
    }

    @Test
    void unserializableParametersDoNotTurnSuccessfulOperationIntoFailure() {
        AtomicReference<OperationLogEntry> recorded = new AtomicReference<>();
        AspectJProxyFactory factory = new AspectJProxyFactory(new UploadTarget());
        factory.setProxyTargetClass(true);
        factory.addAspect(new OperationLogAspect(recorded::set, new ObjectMapper()));
        UploadTarget target = factory.getProxy();

        assertEquals("saved", target.save(new UnserializableRequest()));
        assertEquals(1, recorded.get().result());
        assertEquals("[]", recorded.get().requestParams());
    }

    public static class UnserializableRequest {
        public String getValue() { throw new IllegalStateException("不可序列化"); }
    }

    @Test
    void masksGenericConfigValuesWhenKeyIsProtected() {
        AtomicReference<OperationLogEntry> recorded = new AtomicReference<>();
        AspectJProxyFactory factory = new AspectJProxyFactory(new UploadTarget());
        factory.setProxyTargetClass(true);
        factory.addAspect(new OperationLogAspect(recorded::set, new ObjectMapper()));
        UploadTarget target = factory.getProxy();
        target.save(Map.of("key", "bookkeeping.ocr.api_key", "value", "must-never-be-logged"));
        assertFalse(recorded.get().requestParams().contains("must-never-be-logged"));
    }
}
