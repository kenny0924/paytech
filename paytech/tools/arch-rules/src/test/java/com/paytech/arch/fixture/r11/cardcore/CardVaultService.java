package com.paytech.arch.fixture.r11.cardcore;

import com.paytech.arch.fixture.r11.cardcore.crypto.CardCipher;

public class CardVaultService {

    public String open(String value) {
        return CardCipher.decrypt(value);
    }
}
