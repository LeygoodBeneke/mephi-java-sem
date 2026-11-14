package ru.mephi.shortlink.support;

import java.util.ArrayDeque;
import java.util.Deque;
import ru.mephi.shortlink.core.generation.ShortCodeGenerator;
import ru.mephi.shortlink.core.model.ShortCode;

public final class ScriptedCodeGenerator implements ShortCodeGenerator {

    private final Deque<String> scripted = new ArrayDeque<>();
    private int counter;

    public ScriptedCodeGenerator enqueue(String... codes) {
        for (String code : codes) {
            scripted.addLast(code);
        }
        return this;
    }

    @Override
    public ShortCode generate() {
        String next = scripted.pollFirst();
        if (next == null) {
            counter++;
            next = "SEQ" + String.format("%03d", counter);
        }
        return new ShortCode(next);
    }
}
