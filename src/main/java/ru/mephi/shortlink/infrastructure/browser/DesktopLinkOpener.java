package ru.mephi.shortlink.infrastructure.browser;

import java.awt.Desktop;
import java.net.URI;
import java.net.URISyntaxException;
import ru.mephi.shortlink.core.port.LinkOpener;

public final class DesktopLinkOpener implements LinkOpener {

    @Override
    public OpenResult open(String targetUrl) {
        try {
            if (!Desktop.isDesktopSupported()) {
                return manual(targetUrl, "Среда не поддерживает Desktop API (headless)");
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return manual(targetUrl, "Действие BROWSE недоступно в этой JVM");
            }
            desktop.browse(new URI(targetUrl));
            return new OpenResult(true, "Браузер открыт");
        } catch (URISyntaxException e) {
            return manual(targetUrl, "Адрес не является корректным URI: " + e.getReason());
        } catch (java.io.IOException e) {
            return manual(targetUrl, "Браузер не открылся: " + e.getMessage());
        } catch (RuntimeException | Error e) {
            return manual(targetUrl, "Браузер не открылся: " + e.getClass().getSimpleName() + ": "
                + e.getMessage());
        }
    }

    private static OpenResult manual(String targetUrl, String why) {
        return new OpenResult(false, why + ". Откройте адрес вручную: " + targetUrl);
    }
}
