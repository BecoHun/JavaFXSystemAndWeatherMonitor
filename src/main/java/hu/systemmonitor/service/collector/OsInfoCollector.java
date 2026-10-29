package hu.systemmonitor.service.collector;

import oshi.SystemInfo;
import oshi.software.os.OperatingSystem;

import java.util.Locale;

public final class OsInfoCollector {

    private static final String STATIC_BASE_INFO = resolveBaseInfo();

    private OsInfoCollector() {
    }

    public static String getBaseOsInfo() {
        return STATIC_BASE_INFO;
    }

    private static String resolveBaseInfo() {
        try {
            SystemInfo systemInfo = new SystemInfo();
            OperatingSystem os = systemInfo.getOperatingSystem();

            String osName = os.toString();
            int bitness = os.getBitness();
            String kernelVersion = System.getProperty("os.version");

            String desktop = System.getenv("XDG_CURRENT_DESKTOP");
            if (desktop == null || desktop.isBlank()) {
                desktop = System.getenv("DESKTOP_SESSION");
            }
            if (desktop == null || desktop.isBlank()) {
                desktop = "Ismeretlen";
            }

            // Valós session / ablakrendszer típus meghatározása
            String rawSessionType = System.getenv("XDG_SESSION_TYPE");
            String sessionType;

            if (rawSessionType != null && !rawSessionType.isBlank()) {
                sessionType = switch (rawSessionType.toLowerCase(Locale.ROOT).trim()) {
                    case "wayland" -> "Wayland";
                    case "x11" -> "X11";
                    case "tty" -> "TTY (Konzol)";
                    case "mir" -> "Mir";
                    default -> rawSessionType.toUpperCase(Locale.ROOT);
                };
            } else {
                // Ha nincs XDG_SESSION_TYPE, vizsgáljuk meg a többi környezeti vltozót
                if (System.getenv("WAYLAND_DISPLAY") != null) {
                    sessionType = "Wayland";
                } else if (System.getenv("DISPLAY") != null) {
                    sessionType = "X11";
                } else if (System.getenv("SSH_TTY") != null || System.getenv("SSH_CLIENT") != null) {
                    sessionType = "SSH / Távoli munkamenet";
                } else {
                    String term = System.getenv("TERM");
                    if (term != null && !term.isBlank()) {
                        sessionType = "Konzol (" + term + ")";
                    } else {
                        sessionType = "Ismeretlen";
                    }
                }
            }

            return String.format("%s | Kernel: %s | %d-bit | Asztali környezet: %s | Ablakrendszer: %s",
                    osName, kernelVersion, bitness, desktop, sessionType);
        } catch (Exception e) {
            return "Operációs rendszer adatok nem érhetők el";
        }
    }
}