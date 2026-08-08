package com.example.protobuf.basics.step5_advancedtypes;

import com.example.protobuf.order.OrderDetail;
import com.example.protobuf.support.Hex;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 5. 고급 타입 - optional: 0을 넣은 것과 "값을 안 넣음"을 구분한다.
 * 스키마는 OrderDetail.coupon_amount(optional int64, 2번). 설명은 docs/basics/step5-optional.md
 */
class OptionalTest {

    @Test
    void optional_필드는_0을_넣으면_hasXxx가_true이고_바이너리에도_기록된다() throws InvalidProtocolBufferException {
        OrderDetail notSet = OrderDetail.newBuilder().build();
        OrderDetail setZero = OrderDetail.newBuilder().setCouponAmount(0).build();

        System.out.println("Hex.of(notSet) = " + Hex.of(notSet));
        System.out.println("Hex.of(setZero) = " + Hex.of(setZero));

        // Java 객체: 값(couponAmount_)은 둘 다 0, has 비트(bitField0_)만 다르다
        assertThat(notSet.hasCouponAmount()).isFalse();
        assertThat(setZero.hasCouponAmount()).isTrue();
        assertThat(setZero.getCouponAmount()).isZero();

        // 바이너리: has 비트를 따로 기록하지 않는다. "필드가 바이트에 있느냐"가 곧 has 정보
        //   notSet  -> 2번 필드 자체가 없음 (0바이트)
        //   setZero -> coupon_amount(2번, varint) = 0 -> 태그 10, 값 00. 기본값이어도 "넣었다"는 사실을 남긴다
        assertThat(Hex.of(notSet)).isEmpty();
        assertThat(Hex.of(setZero)).isEqualTo("10 00");

        // 받는 쪽: 태그 10을 읽으면 has 비트를 켜고, 못 읽으면 초기값(0, has = false) 그대로 남는다
        OrderDetail receivedNotSet = OrderDetail.parseFrom(notSet.toByteArray());
        OrderDetail receivedSetZero = OrderDetail.parseFrom(setZero.toByteArray());
        assertThat(receivedNotSet.hasCouponAmount()).isFalse();
        assertThat(receivedSetZero.hasCouponAmount()).isTrue();
        assertThat(receivedSetZero.getCouponAmount()).isZero();
    }

    @Test
    void optional_필드를_넣지_않고_get하면_null이_아니라_기본값을_돌려준다() {
        OrderDetail notSet = OrderDetail.newBuilder().build();

        // null도 예외도 아닌 0. 0과 미설정을 구분하려면 반드시 hasXxx()를 먼저 확인해야 한다
        assertThat(notSet.hasCouponAmount()).isFalse();
        assertThat(notSet.getCouponAmount()).isZero();

        // 메시지 필드도 null이 아니라 빈 기본 인스턴스
        assertThat(notSet.hasOrderedAt()).isFalse();
        assertThat(notSet.getOrderedAt()).isSameAs(Timestamp.getDefaultInstance());
    }

    @Test
    void optional_필드에_null을_set하면_NullPointerException이_발생한다() {
        Long nullCoupon = null;

        // setCouponAmount는 long을 받으므로 null인 Long은 언박싱하면서 NPE
        assertThatThrownBy(() -> OrderDetail.newBuilder().setCouponAmount(nullCoupon))
                .isInstanceOf(NullPointerException.class);

        // 객체를 받는 setter도 null을 허용하지 않는다. 값을 지우려면 clearXxx()를 쓴다
        assertThatThrownBy(() -> OrderDetail.newBuilder().setOrderedAt((Timestamp) null))
                .isInstanceOf(NullPointerException.class);
    }
}
