package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.service.*;
import com.scaffold.bookkeeping.domain.vo.OcrConfigStatusVO;
import com.scaffold.framework.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OcrControllerTest {
    final OcrService tasks = mock(OcrService.class);
    final OcrConfigService config = mock(OcrConfigService.class);
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new OcrController(tasks,config)).setControllerAdvice(new GlobalExceptionHandler()).build();
    @Test void startRejectsMissingAttachmentVersionAndUnsupportedType() throws Exception {
        for (String body : new String[]{"{}", "{\"attachmentId\":1,\"version\":-1}", "{\"attachmentId\":0,\"version\":0}", "{\"attachmentId\":1,\"version\":0,\"documentType\":\"IdCard\"}"}) {
            mvc.perform(post("/api/bookkeeping/records/9/ocr").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(tasks);
    }
    @Test void configIsWriteOnlyAndResponseDoesNotCacheSecrets() throws Exception {
        mvc.perform(put("/api/bookkeeping/ocr/config").contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"aliyun\",\"apiKey\":\"test-secret\",\"accessKeyId\":\"test-id\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        verify(config).save(argThat(request -> request.getApiKey().equals("test-secret") && request.getAccessKeyId().equals("test-id")));
        when(config.status()).thenReturn(new OcrConfigStatusVO("aliyun", "", true,true,"******1234","******5678",true));
        mvc.perform(get("/api/bookkeeping/ocr/config/status")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.keySuffix").value("******1234"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
    }
    @Test void confirmationRequiresCompleteValidatedDraft() throws Exception {
        mvc.perform(post("/api/bookkeeping/ocr/tasks/1/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tasks);
    }
}
