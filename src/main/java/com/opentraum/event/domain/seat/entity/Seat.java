package com.opentraum.event.domain.seat.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("seats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    private Long id;

    private Long scheduleId;

    private String grade;

    private String zone;

    private String seatNumber;

    private Integer price;

    /**
     * 좌석 상태 (AVAILABLE / HELD / SOLD).
     */
    private String status;

    /**
     * HOLD 만료 시각. 조회 시 만료된 HELD를 AVAILABLE로 보정할 때 사용.
     * AVAILABLE / SOLD 상태에서는 NULL.
     */
    @Column("held_until")
    private LocalDateTime heldUntil;

    /**
     * 해당 좌석을 HOLD/SOLD 점유한 reservation.id.
     * AVAILABLE 상태에서는 NULL.
     */
    @Column("reservation_id")
    private Long reservationId;

    private LocalDateTime createdAt;
}
