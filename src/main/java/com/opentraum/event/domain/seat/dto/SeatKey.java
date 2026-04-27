package com.opentraum.event.domain.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SAGA 배치 좌석 작업용 좌석 식별자.
 *
 * <p>seatId는 이벤트 발행 시점에만 채워지면 되므로 호출자가 모를 수 있다.
 * 서비스 내부에서 DB 조회로 seatId를 채운 뒤 Outbox payload에 넣는다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatKey {
    private Long seatId;
    private String zone;
    private String seatNumber;
}
