package com.paytech.arch.fixture.r11.paymentcore;

import com.paytech.arch.fixture.r11.cardcore.crypto.CardCipher;

public class PaymentCardReader {

    public String open(String value) {
        return CardCipher.decrypt(value);
    }
}
