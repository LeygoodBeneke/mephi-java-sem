package ru.mephi.shortlink.infrastructure.browser;

import java.io.PrintWriter;
import ru.mephi.shortlink.core.port.LinkOpener;

public final class ConsoleLinkOpener implements LinkOpener {

    private final PrintWriter out;

    public ConsoleLinkOpener(PrintWriter out) {
        this.out = out;
    }

    @Override
    public OpenResult open(String targetUrl) {
        out.println("    переход по ссылке (браузер отключён настройкой browser.open):");
        out.println("    " + targetUrl);
        return new OpenResult(false, "Браузер отключён — адрес выведен в консоль");
    }
}
