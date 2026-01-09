package com.playprobie.api.domain.streaming.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.playprobie.api.domain.streaming.application.CapacityChangeAsyncService;
import com.playprobie.api.domain.streaming.dao.CapacityChangeRequestRepository;
import com.playprobie.api.domain.streaming.domain.CapacityChangeRequest;
import com.playprobie.api.domain.streaming.domain.CapacityChangeType;
import com.playprobie.api.domain.streaming.domain.RequestStatus;
import com.playprobie.api.domain.streaming.domain.StreamingResource;

@ExtendWith(MockitoExtension.class)
class CapacityChangeReconcilerTest {

	@Mock
	private CapacityChangeRequestRepository requestRepository;

	@Mock
	private CapacityChangeAsyncService asyncService;

	@InjectMocks
	private CapacityChangeReconciler reconciler;

	@Test
	@DisplayName("Reconcile: PENDING 상태로 1분 이상 지난 요청을 찾아 재처리한다")
	void reconcile_Success() {
		// given
		StreamingResource resource = mock(StreamingResource.class);
		given(resource.getId()).willReturn(100L);

		// ID 설정을 위해 리플렉션이나 모킹이 필요하지만, 여기서는 getRequest 로직 단순화를 위해 spy나 mock 사용하지 않고
		// 실제 객체 사용 시 ID는 null일 수 있음.
		// 하지만 asyncService 호출 시 getId()를 사용하므로, mock 객체로 대체하는 것이 안전함.

		CapacityChangeRequest mockRequest1 = mock(CapacityChangeRequest.class);
		given(mockRequest1.getId()).willReturn(1L);
		given(mockRequest1.getResource()).willReturn(resource);
		given(mockRequest1.getType()).willReturn(CapacityChangeType.START_TEST);
		given(mockRequest1.getTargetCapacity()).willReturn(1);

		CapacityChangeRequest mockRequest2 = mock(CapacityChangeRequest.class);
		given(mockRequest2.getId()).willReturn(2L);
		given(mockRequest2.getResource()).willReturn(resource);
		given(mockRequest2.getType()).willReturn(CapacityChangeType.STOP_TEST);
		given(mockRequest2.getTargetCapacity()).willReturn(0);

		given(requestRepository.findAllByStatusAndCreatedAtBefore(eq(RequestStatus.PENDING), any(LocalDateTime.class)))
			.willReturn(List.of(mockRequest1, mockRequest2));

		// when
		reconciler.reconcile();

		// then
		verify(asyncService, times(1)).applyCapacityChange(100L, 1L, 1, CapacityChangeType.START_TEST);
		verify(asyncService, times(1)).applyCapacityChange(100L, 2L, 0, CapacityChangeType.STOP_TEST);
	}
}
