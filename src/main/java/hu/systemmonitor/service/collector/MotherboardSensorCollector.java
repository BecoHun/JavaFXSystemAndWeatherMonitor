package hu.systemmonitor.service.collector;

import hu.systemmonitor.model.SensorRow;
import oshi.SystemInfo;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MotherboardSensorCollector {

    private static final SystemInfo SYSTEM_INFO = new SystemInfo();
    private static final HardwareAbstractionLayer HARDWARE = SYSTEM_INFO.getHardware();
    private static final ComputerSystem COMPUTER_SYSTEM = HARDWARE.getComputerSystem();

    public static String getMotherboardInfo() {
        try {
            String boardManufacturer = COMPUTER_SYSTEM.getBaseboard().getManufacturer();
            String boardModel = COMPUTER_SYSTEM.getBaseboard().getModel();

            String biosVersion = COMPUTER_SYSTEM.getFirmware().getVersion();
            String biosReleaseDate = COMPUTER_SYSTEM.getFirmware().getReleaseDate();

            String motherboardInfo = (boardManufacturer + " " + boardModel).trim();
            if (motherboardInfo.isEmpty()) {
                motherboardInfo = COMPUTER_SYSTEM.getManufacturer() + " " + COMPUTER_SYSTEM.getModel();
            }

            if (biosVersion != null && !biosVersion.isBlank()) {
                motherboardInfo += " | BIOS: " + biosVersion;
                if (biosReleaseDate != null && !biosReleaseDate.isBlank()) {
                    motherboardInfo += " (" + biosReleaseDate + ")";
                }
            }

            return motherboardInfo.isBlank() ? "Ismeretlen alaplap" : motherboardInfo;
        } catch (Exception e) {
            return "Alaplapi adatok nem érhetők el";
        }
    }

    public static List<SensorRow> readSensors() {
        List<SensorRow> result = new ArrayList<>();
        boolean foundBoard = false;

        try {
            Path hwmonRoot = Path.of("/sys/class/hwmon");
            if (Files.isDirectory(hwmonRoot)) {
                try (var hwmons = Files.list(hwmonRoot)) {
                    for (Path hwmon : hwmons.toList()) {
                        if (!Files.isDirectory(hwmon)) continue;
                        String chipName = readText(hwmon.resolve("name"));
                        if ("amdgpu".equalsIgnoreCase(chipName) || chipName.contains("radeon") || chipName.contains("nvidia")) {
                            continue;
                        }

                        foundBoard = true;
                        readHumanReadableTemperature(hwmon, result);
                        readHumanReadableFans(hwmon, result);
                        readHumanReadableVoltages(hwmon, result);
                    }
                } catch (IOException ignored) {}
            }
        } catch (Exception ignored) {}

        if (result.isEmpty() || !foundBoard) {
            result.add(new SensorRow("Állapot", "Alaplapi szenzoradatok nem érhetők el."));
        }
        return result;
    }

    private static void readHumanReadableTemperature(Path hwmon, List<SensorRow> result) {
        try (var files = Files.list(hwmon)) {
            for (Path input : files.filter(p -> p.getFileName().toString().matches("temp[0-9]+_input")).toList()) {
                String fileName = input.getFileName().toString();
                String prefix = fileName.substring(0, fileName.length() - "_input".length());
                String label = readText(hwmon.resolve(prefix + "_label"));
                if (label.isBlank()) {
                    label = switch (prefix) {
                        case "temp1" -> "Alaplapi hőmérséklet";
                        case "temp2" -> "Processzor mag hőmérséklet";
                        case "temp3" -> "Rendszer hőmérséklet";
                        default -> "Hőmérséklet (" + prefix + ")";
                    };
                } else {
                //    label = "Hőmérséklet – " + label;
                }

                long milliCelsius = readLong(input, -1);
                if (milliCelsius >= 0) {
                    double tempC = milliCelsius / 1000.0;
                    if (tempC > 0 && tempC < 125) {
                        result.add(new SensorRow(label, String.format(Locale.ROOT, "%.1f °C", tempC)));
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private static void readHumanReadableFans(Path hwmon, List<SensorRow> result) {
        try (var files = Files.list(hwmon)) {
            for (Path input : files.filter(p -> p.getFileName().toString().matches("fan[0-9]+_input")).toList()) {
                String fileName = input.getFileName().toString();
                String prefix = fileName.substring(0, fileName.length() - "_input".length());
                String label = readText(hwmon.resolve(prefix + "_label"));
                if (label.isBlank()) {
                    label = prefix.replace("fan", "Ventilátor ");
                } else {
                    label = "Ventilátor – " + label;
                }

                long rpm = readLong(input, -1);
                if (rpm >= 0) {
                    result.add(new SensorRow(label, rpm + " RPM"));
                }
            }
        } catch (IOException ignored) {}
    }

    private static void readHumanReadableVoltages(Path hwmon, List<SensorRow> result) {
        try (var files = Files.list(hwmon)) {
            for (Path input : files.filter(p -> p.getFileName().toString().matches("in[0-9]+_input")).toList()) {
                String fileName = input.getFileName().toString();
                String prefix = fileName.substring(0, fileName.length() - "_input".length());
                String label = readText(hwmon.resolve(prefix + "_label"));
                if (label.isBlank()) {
                    label = "Feszültség (" + prefix + ")";
                } else {
                    label = "Feszültség – " + label;
                }

                long millivolts = readLong(input, -1);
                if (millivolts >= 0) {
                    result.add(new SensorRow(label, String.format(Locale.ROOT, "%.3f V", millivolts / 1000.0)));
                }
            }
        } catch (IOException ignored) {}
    }

    private static String readText(Path path) {
        if (!Files.isReadable(path)) return "";
        try { return Files.readString(path).trim(); } catch (IOException ignored) { return ""; }
    }

    private static long readLong(Path path, long fallback) {
        String value = readText(path);
        if (value.isBlank()) return fallback;
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return fallback; }
    }
}