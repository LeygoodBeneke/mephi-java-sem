package ru.mephi.shortlink.core.generation;

import java.security.SecureRandom;
import ru.mephi.shortlink.core.model.ShortCode;

public final class Base62CodeGenerator implements ShortCodeGenerator {

    private static final char[] ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final int length;

    public Base62CodeGenerator(int length) {
        if (length < 4 || length > 12) {
            throw new IllegalArgumentException("Длина кода должна быть 4..12, получено: " + length);
        }
        this.length = length;
    }

    @Override
    public ShortCode generate() {
        char[] code = new char[length];
        for (int i = 0; i < length; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new ShortCode(new String(code));
    }
}
