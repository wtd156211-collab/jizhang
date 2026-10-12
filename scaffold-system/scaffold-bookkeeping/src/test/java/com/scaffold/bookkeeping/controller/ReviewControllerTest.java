package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.domain.ReviewAction;
import com.scaffold.bookkeeping.domain.dto.ReviewActionRequest;
import com.scaffold.bookkeeping.service.RecordHistoryService;
import com.scaffold.bookkeeping.service.ReviewService;
import com.scaffold.framework.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReviewControllerTest {
    private final ReviewService service = mock(ReviewService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReviewController(service, mock(RecordHistoryService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void rejectsMissingNegativeAndMalformedVersionsBeforeReachingService() throws Exception {
        for (String body : new String[]{"{}", "{\"version\":-1}", "{\"version\":\"invalid\"}", "{invalid}"}) {
            mvc.perform(post("/api/bookkeeping/records/9/submit").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test
    void validatesReasonLimitAndMapsReturnToFixedAction() throws Exception {
        mvc.perform(post("/api/bookkeeping/records/9/review/return").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3,\"reason\":\"" + "字".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        mvc.perform(post("/api/bookkeeping/records/9/review/return").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3,\"reason\":\"补充票据\",\"toStatus\":\"POSTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).act(eq(9L), eq(ReviewAction.RETURN), argThat((ReviewActionRequest request) ->
                request.getVersion() == 3 && "补充票据".equals(request.getReason())));
    }
}
