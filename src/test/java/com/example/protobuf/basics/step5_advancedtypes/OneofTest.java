package com.example.protobuf.basics.step5_advancedtypes;

import com.example.protobuf.order.BankTransferPayment;
import com.example.protobuf.order.CardPayment;
import com.example.protobuf.order.OrderDetail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 5. 고급 타입 - oneof: 여러 필드 중 하나만 설정된다. 다른 필드를 set하면 이전 값이 지워진다.
 * 스키마는 OrderDetail.payment_method(card 3번, bank_transfer 4번). 설명은 docs/basics/step5-oneof.md
 */
class OneofTest {

    @Test
    void oneof는_다른_필드를_set하면_이전_값이_지워진다() {
        OrderDetail cardOnly = OrderDetail.newBuilder()
                .setCard(CardPayment.newBuilder().setCardNumber("1234-5678").setInstallmentMonths(3))
                .build();

        // 카드를 넣은 상태에서 계좌이체를 set
        OrderDetail cardThenBank = cardOnly.toBuilder()
                .setBankTransfer(BankTransferPayment.newBuilder().setBankName("kb").setAccountNumber("111-222"))
                .build();

        System.out.println("[카드만 set]\n" + cardOnly);
        System.out.println("[카드 → 계좌이체 set]\n" + cardThenBank);

        assertThat(cardOnly.getPaymentMethodCase()).isEqualTo(OrderDetail.PaymentMethodCase.CARD);
        assertThat(cardOnly.hasCard()).isTrue();

        // 계좌이체를 set하면 카드 정보는 사라진다
        assertThat(cardThenBank.getPaymentMethodCase()).isEqualTo(OrderDetail.PaymentMethodCase.BANK_TRANSFER);
        assertThat(cardThenBank.hasCard()).isFalse();
        assertThat(cardThenBank.getCard()).isEqualTo(CardPayment.getDefaultInstance());
        assertThat(cardThenBank.getBankTransfer().getBankName()).isEqualTo("kb");
    }

    @Test
    void oneof에_아무것도_넣지_않으면_NOT_SET이다() {
        OrderDetail detail = OrderDetail.newBuilder().build();

        assertThat(detail.getPaymentMethodCase()).isEqualTo(OrderDetail.PaymentMethodCase.PAYMENTMETHOD_NOT_SET);
        assertThat(detail.toBuilder().clearPaymentMethod().build()).isEqualTo(detail);
    }
}
