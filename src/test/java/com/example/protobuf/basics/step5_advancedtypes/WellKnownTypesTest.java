package com.example.protobuf.basics.step5_advancedtypes;

import com.example.protobuf.order.OrderDetail;
import com.example.protobuf.support.Hex;
import com.google.protobuf.Duration;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.Timestamps;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 5. 고급 타입 - Well-Known Types: Timestamp, Duration은 평범한 중첩 메시지로 기록되고, JSON에서는 문자열로 표현된다.
 * 스키마는 OrderDetail.ordered_at(Timestamp, 6번), delivery_time(Duration, 7번). 설명은 docs/basics/step5-well-known-types.md
 */
class WellKnownTypesTest {

    @Test
    void ordered_at은_seconds와_nanos를_가진_Timestamp_메시지로_기록된다() throws InvalidProtocolBufferException {
        Timestamp orderedAt = Timestamps.parseUnchecked("2026-10-28T01:02:03.456Z");

        OrderDetail sent = OrderDetail.newBuilder()
                .setOrderedAt(orderedAt) // google.protobuf.Timestamp
                .build();

        // Timestamp는 특별한 타입이 아니라 평범한 메시지: ordered_at(6번, LEN) 안에 seconds(1번), nanos(2번)
        //   32 0c | 08 8b 93 85 d7 06 | 10 80 84 b8 d9 01
        //         | seconds 1793149323 | nanos 456000000
        assertThat(Hex.of(sent)).isEqualTo("32 0c 08 8b 93 85 d7 06 10 80 84 b8 d9 01");

        OrderDetail received = OrderDetail.parseFrom(sent.toByteArray());
        System.out.println("received.getOrderedAt() = " + Timestamps.toString(received.getOrderedAt()));

        assertThat(received.getOrderedAt()).isEqualTo(orderedAt);
    }

    @Test
    void delivery_time은_seconds와_nanos를_가진_Duration_메시지로_기록된다() throws InvalidProtocolBufferException {
        Duration deliveryTime = Durations.parseUnchecked("172800.5s");   // 2일 + 0.5초

        OrderDetail sent = OrderDetail.newBuilder()
                .setDeliveryTime(deliveryTime) // google.protobuf.Duration
                .build();

        // Duration도 평범한 메시지: delivery_time(7번, LEN) 안에 seconds(1번), nanos(2번)
        //   3a 0a | 08 80 c6 0a    | 10 80 ca b5 ee 01
        //         | seconds 172800 | nanos 500000000
        assertThat(Hex.of(sent)).isEqualTo("3a 0a 08 80 c6 0a 10 80 ca b5 ee 01");

        OrderDetail received = OrderDetail.parseFrom(sent.toByteArray());
        System.out.println("received.getDeliveryTime() = " + Durations.toString(received.getDeliveryTime()));

        assertThat(received.getDeliveryTime()).isEqualTo(deliveryTime);
    }

    @Test
    void Timestamp와_Duration은_JSON에서_문자열로_표현된다() throws InvalidProtocolBufferException {
        OrderDetail detail = OrderDetail.newBuilder()
                .setOrderedAt(Timestamp.newBuilder().setSeconds(1_793_149_323L))
                .setDeliveryTime(Duration.newBuilder().setSeconds(172_800L))
                .build();
        System.out.println("detail = " + detail);

        String json = JsonFormat.printer().omittingInsignificantWhitespace().print(detail);
        System.out.println("json = " + json);

        // {"seconds": ..., "nanos": ...} 가 아니라 RFC 3339 시각, "초s" 문자열
        assertThat(json).isEqualTo("{\"orderedAt\":\"2026-10-28T01:02:03Z\",\"deliveryTime\":\"172800s\"}");

        OrderDetail.Builder parsed = OrderDetail.newBuilder();
        JsonFormat.parser().merge(json, parsed);
        System.out.println("parsed = " + parsed);
        assertThat(parsed.build()).isEqualTo(detail);
    }
}
