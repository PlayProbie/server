package com.playprobie.api.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExceptionTestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("@Valid 검증 실패 시 400 반환 및 에러 상세 정보 포함")
    void MethodArgumentNotValidException시_400_반환() throws Exception {
        String invalidJson = """
                {
                	"name": ""
                }
                """;

        mockMvc.perform(post("/test/exception/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.message").value("잘못된 입력값입니다."))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("BusinessException 발생 시 해당 ErrorCode 반환")
    void BusinessException시_해당_ErrorCode_반환() throws Exception {
        mockMvc.perform(get("/test/exception/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("일반 Exception 발생 시 500 반환")
    void 일반_Exception시_500_반환() throws Exception {
        mockMvc.perform(get("/test/exception/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("C004"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드 호출 시 405 반환")
    void 지원하지_않는_HTTP_메서드시_405_반환() throws Exception {
        mockMvc.perform(post("/test/exception/business")) // GET만 허용된 엔드포인트에 POST
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("C002"));
    }
}
