package ru.mephi.shortlink.core.port;

public interface LinkOpener {

    OpenResult open(String targetUrl);

    record OpenResult(boolean browserLaunched, String message) {
    }
}
