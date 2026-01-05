package com.playprobie.api.domain.streaming.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.streaming.application.StreamingResourceService;
import com.playprobie.api.domain.streaming.dto.CreateStreamingResourceRequest;
import com.playprobie.api.domain.streaming.dto.StreamingResourceResponse;
import com.playprobie.api.global.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 스트리밍 리소스 관리 Controller (Admin).
 * 
 * <p>
 * 리소스 할당, 조회, 해제 기능을 제공합니다.
 */
@RestController
@RequestMapping("/surveys/{surveyId}/streaming-resource")
@RequiredArgsConstructor
public class StreamingResourceController {

    private final StreamingResourceService streamingResourceService;

    /**
     * 스트리밍 리소스를 할당합니다.
     * 
     * <p>
     * POST /surveys/{surveyId}/streaming-resource
     * 
     * @param surveyId Survey PK
     * @param request  할당 요청
     * @return 201 Created
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StreamingResourceResponse>> createResource(
            @PathVariable java.util.UUID surveyId,
            @Valid @RequestBody CreateStreamingResourceRequest request) {

        StreamingResourceResponse response = streamingResourceService.createResource(surveyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    /**
     * 스트리밍 리소스를 조회합니다.
     * 
     * <p>
     * GET /surveys/{surveyId}/streaming-resource
     * 
     * @param surveyId Survey PK
     * @return 200 OK
     */
    @GetMapping
    public ResponseEntity<ApiResponse<StreamingResourceResponse>> getResource(@PathVariable java.util.UUID surveyId) {
        StreamingResourceResponse response = streamingResourceService.getResourceByUuid(surveyId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * 스트리밍 리소스를 해제합니다.
     * 
     * <p>
     * DELETE /surveys/{surveyId}/streaming-resource
     * 
     * @param surveyId Survey PK
     * @return 204 No Content
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteResource(@PathVariable java.util.UUID surveyId) {
        streamingResourceService.deleteResource(surveyId);
        return ResponseEntity.noContent().build();
    }
}
