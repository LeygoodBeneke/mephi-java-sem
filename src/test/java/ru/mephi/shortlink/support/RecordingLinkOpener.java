package ru.mephi.shortlink.support;

import java.util.ArrayList;
import java.util.List;
import ru.mephi.shortlink.core.port.LinkOpener;

public final class RecordingLinkOpener implements LinkOpener {

    private final List<String> opened = new ArrayList<>();
    private boolean failNext;

    @Override
    public synchronized OpenResult open(String targetUrl) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("мнимый сбой браузера");
        }
        opened.add(targetUrl);
        return new OpenResult(true, "браузер смоделирован");
    }

    public synchronized List<String> openedUrls() {
        return List.copyOf(opened);
    }

    public synchronized int openCount() {
        return opened.size();
    }

    public void failNextOpen() {
        failNext = true;
    }
}
