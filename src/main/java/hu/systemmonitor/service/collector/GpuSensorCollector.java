package hu.systemmonitor.service.collector;

import hu.systemmonitor.model.SensorRow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GpuSensorCollector {

    public static List<SensorRow> readSensors() {
        List<SensorRow> result = new ArrayList<>();
        boolean foundGpu = false;

        try {
            Path drmPath = Path.of("/sys/class/drm");
            if (!Files.isDirectory(drmPath)) return result;

            try (var cards = Files.list(drmPath)) {
                List<Path> cardPaths = cards.filter(p -> p.getFileName().toString().matches("card[0-9]+")).toList();

                for (Path card : cardPaths) {
                    Path device = card.resolve("device");
                    if (!Files.exists(device)) continue;

                    String driverName = "";
                    try {
                        Path driverLink = device.resolve("driver");
                        if (Files.isSymbolicLink(driverLink)) {
                            driverName = driverLink.toRealPath().getFileName().toString();
                        }
                    } catch (IOException ignored) {}

                    Path hwmonRoot = device.resolve("hwmon");
                    if (Files.isDirectory(hwmonRoot)) {
                        try (var hwmons = Files.list(hwmonRoot)) {
                            for (Path hwmon : hwmons.toList()) {
                                if (!Files.isDirectory(hwmon)) continue;
                                String chipName = readText(hwmon.resolve("name"));
                                if (!"amdgpu".equalsIgnoreCase(chipName) && !"amdgpu".equalsIgnoreCase(driverName)) {
                                    continue;
                                }

                                foundGpu = true;
                                readTemperatureSensors(hwmon, result);
                                readFanSensors(hwmon, result);
                                readVoltageSensors(hwmon, result);
                                readPowerSensors(hwmon, result);
                            }
                        } catch (IOException ignored) {}
                    }

                    if ("amdgpu".equalsIgnoreCase(driverName)) {
                        foundGpu = true;
                        addNumericMetric(device.resolve("gpu_busy_percent"), "GPU terhelés", "%", 1.0, result);
                        readActiveClock(device.resolve("pp_dpm_sclk"), "GPU órajel", result);
                        readActiveClock(device.resolve("pp_dpm_mclk"), "VRAM órajel", result);
                        addByteMetric(device.resolve("mem_info_vram_used"), "VRAM használt", result);
                        addByteMetric(device.resolve("mem_info_vram_total"), "VRAM összesen", result);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (result.isEmpty()) {
            result.add(new SensorRow("Állapot", "GPU szenzoradatok nem érhetők el."));
        }
        return result;
    }

    private static void readTemperatureSensors(Path hwmon, List<SensorRow> result) {
        try (var files = Files.list(hwmon)) {
            for (Path input : files.filter(p -> p.getFileName().toString().matches("temp[0-9]+_input")).toList()) {
                String fileName = input.getFileName().toString();
                String prefix = fileName.substring(0, fileName.length() - "_input".length());
                String label = readText(hwmon.resolve(prefix + "_label"));
                if (label.isBlank()) label = prefix;

                String huLabel = switch (label.toLowerCase()) {
                    case "edge" -> "GPU peremhőmérséklet";
                    case "junction" -> "GPU legforróbb pontja";
                    case "mem" -> "GPU memória";
                    default -> label;
                };

                long milliCelsius = readLong(input);
                if (milliCelsius >= 0) {
                    result.add(new SensorRow(huLabel, String.format(Locale.ROOT, "%.1f °C", milliCelsius / 1000.0)));
                }
            }
        } catch (IOException ignored) {}
    }

    private static void readFanSensors(Path hwmon, List<SensorRow> result) {
        try (var files = Files.list(hwmon)) {
            for (Path input : files.filter(p -> p.getFileName().toString().matches("fan[0-9]+_input")).toList()) {
                long rpm = readLong(input);
                if (rpm >= 0) result.add(new SensorRow("Ventilátor fordulatszám", rpm + " RPM"));
            }
        } catch (IOException ignored) {}
    }

    private static void readVoltageSensors(Path hwmon, List<SensorRow> result) {
        try (var files = Files.list(hwmon)) {
            for (Path input : files.filter(p -> p.getFileName().toString().matches("in[0-9]+_input")).toList()) {
                String fileName = input.getFileName().toString();
                String prefix = fileName.substring(0, fileName.length() - "_input".length());
                String label = readText(hwmon.resolve(prefix + "_label"));
                if (label.isBlank()) label = prefix;

                long millivolts = readLong(input);
                if (millivolts >= 0) result.add(new SensorRow("Feszültség (" + label + ")", String.format(Locale.ROOT, "%.3f V", millivolts / 1000.0)));
            }
        } catch (IOException ignored) {}
    }

    private static void readPowerSensors(Path hwmon, List<SensorRow> result) {
        try (var files = Files.list(hwmon)) {
            for (Path input : files.filter(p -> p.getFileName().toString().matches("power[0-9]+_average")).toList()) {
                long microwatts = readLong(input);
                if (microwatts >= 0) result.add(new SensorRow("Teljesítményfelvétel", String.format(Locale.ROOT, "%.1f W", microwatts / 1_000_000.0)));
            }
        } catch (IOException ignored) {}
    }

    private static void addNumericMetric(Path path, String label, String unit, double divisor, List<SensorRow> result) {
        long value = readLong(path);
        if (value >= 0) result.add(new SensorRow(label, String.format(Locale.ROOT, "%.1f", value / divisor) + unit));
    }

    private static void addByteMetric(Path path, String label, List<SensorRow> result) {
        long bytes = readLong(path);
        if (bytes >= 0) result.add(new SensorRow(label, formatBytes(bytes)));
    }

    private static void readActiveClock(Path path, String label, List<SensorRow> result) {
        if (!Files.isReadable(path)) return;
        try {
            for (String line : Files.readAllLines(path)) {
                if (line.contains("*")) {
                    Matcher matcher = Pattern.compile("(\\d+)\\s*Mhz").matcher(line);
                    if (matcher.find()) {
                        result.add(new SensorRow(label, matcher.group(1) + " MHz"));
                    } else {
                        result.add(new SensorRow(label, line.replace("*", "").trim()));
                    }
                    return;
                }
            }
        } catch (IOException ignored) {}
    }

    private static String readText(Path path) {
        if (!Files.isReadable(path)) return "";
        try { return Files.readString(path).trim(); } catch (IOException ignored) { return ""; }
    }

    private static long readLong(Path path) {
        String value = readText(path);
        if (value.isBlank()) return -1;
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return -1; }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "—";
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int i = 0;
        while (value >= 1024 && i < units.length - 1) {
            value /= 1024;
            i++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[i]);
    }
}