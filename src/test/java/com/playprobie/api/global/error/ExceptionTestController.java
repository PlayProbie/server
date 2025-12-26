package com.playprobie.api.global.error;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.global.error.exception.EntityNotFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 테스트 전용 컨트롤러 - GlobalExceptionHandler 동작 검증용
 */
@RestController
@RequestMapping("/test/exception")
public class ExceptionTestController {

    @PostMapping("/validation")
    public void triggerValidation(@Valid @RequestBody TestDto dto) {
        // @Valid 검증 실패 시 MethodArgumentNotValidException 발생
    }

    @GetMapping("/business")
    public void triggerBusinessException() {
        throw new EntityNotFoundException();
    }

    @GetMapping("/internal")
    public void triggerInternalException() {
        throw new RuntimeException("테스트 에러");
    }

    // 테스트용 DTO
    public record TestDto(@NotBlank String name) {
    }
}
