package hu.systemmonitor.service.collector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class GpuDetector {

    private static final Pattern PCI_ID = Pattern.compile("\\[([0-9a-fA-F]{4})]");
    private static final Pattern GPU_CLASS = Pattern.compile("\\[(0300|0302|0380)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern VULKAN_GPU_BLOCK = Pattern.compile("(?ms)^GPU\\d+:.*?(?=^GPU\\d+:|\\z)");

    private GpuDetector() {
    }

    /** Kompatibilitási réteg a meglévő rendszerrel (visszaadja a megjelenítendő GPU nevet). */
    public static String getGpuName() {
        return getDisplayText();
    }

    public record GpuInfo(
            String pciAddress,
            String name,
            String vendorId,
            String deviceId,
            String subsystemVendorId,
            String subsystemDeviceId,
            String driver,
            String nameSource) {
    }

    private record RawGpu(
            String pciAddress,
            String className,
            String vendorName,
            String deviceName,
            String subsystemVendorName,
            String subsystemDeviceName,
            String vendorId,
            String deviceId,
            String subsystemVendorId,
            String subsystemDeviceId,
            String driver) {

        String idPair() {
            return vendorId + ":" + deviceId;
        }
    }

    public static List<GpuInfo> detectGpus() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return List.of();
        }

        String lspciOutput = runCommand("lspci", "-D", "-vmm", "-nn");
        List<RawGpu> rawGpus = parseLspci(lspciOutput);
        if (rawGpus.isEmpty()) {
            return List.of();
        }

        Map<String, String> nvidiaNames = readNvidiaNames();
        Map<String, List<String>> vulkanNames = readVulkanNames();
        String openGlRenderer = rawGpus.size() == 1 ? readOpenGlRenderer() : "";

        Map<String, Long> idCounts = rawGpus.stream().collect(
                Collectors.groupingBy(RawGpu::idPair, Collectors.counting()));

        List<GpuInfo> result = new ArrayList<>();
        for (RawGpu gpu : rawGpus) {
            String name = "";
            String source = "PCI-adatbázis";

            String nvidiaName = nvidiaNames.get(normalizeBdf(gpu.pciAddress()));
            if (isUsefulName(nvidiaName)) {
                name = nvidiaName;
                source = "nvidia-smi";
            }

            if (name.isBlank() && isSpecificSubsystemName(gpu.subsystemDeviceName(), gpu.deviceName())) {
                name = gpu.subsystemDeviceName();
                source = "PCI alrendszer-adatbázis";
            }

            if (name.isBlank() && idCounts.getOrDefault(gpu.idPair(), 0L) == 1) {
                List<String> candidates = vulkanNames.getOrDefault(gpu.idPair(), List.of());
                if (candidates.size() == 1 && isUsefulName(candidates.getFirst())) {
                    name = candidates.getFirst();
                    source = "Vulkan illesztőprogram";
                }
            }

            if (name.isBlank() && rawGpus.size() == 1 && isUsefulName(openGlRenderer)) {
                name = openGlRenderer;
                source = "OpenGL illesztőprogram";
            }

            if (name.isBlank()) {
                String subsystem = gpu.subsystemDeviceName();
                if (isUsefulName(subsystem) && !subsystem.contains("/")) {
                    name = subsystem;
                    source = "PCI-adatbázis (alrendszer)";
                } else {
                    name = shortVendorName(gpu.vendorId(), gpu.vendorName()) + " " + gpu.deviceName();
                }
            }

            result.add(new GpuInfo(
                    gpu.pciAddress(),
                    name,
                    gpu.vendorId(),
                    gpu.deviceId(),
                    gpu.subsystemVendorId(),
                    gpu.subsystemDeviceId(),
                    gpu.driver(),
                    source));
        }
        return List.copyOf(result);
    }

    public static String getDisplayText() {
        List<GpuInfo> gpus = detectGpus();
        if (gpus.isEmpty()) {
            return "Nem sikerült PCI-alapú videóadaptert felismerni (ellenőrizd a pciutils/lspci telepítését).";
        }
        if (gpus.size() == 1) {
            return gpus.getFirst().name();
        }
        return gpus.stream()
                .map(gpu -> gpu.name() + " [" + gpu.pciAddress() + "]")
                .collect(Collectors.joining(" | "));
    }

    private static List<RawGpu> parseLspci(String output) {
        List<RawGpu> result = new ArrayList<>();
        Map<String, String> record = new LinkedHashMap<>();

        for (String line : output.split("\\R")) {
            if (line.isBlank()) {
                addGpuRecord(record, result);
                record.clear();
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                record.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        addGpuRecord(record, result);
        return result;
    }

    private static void addGpuRecord(Map<String, String> record, List<RawGpu> result) {
        String className = record.getOrDefault("Class", "");
        if (!GPU_CLASS.matcher(className).find()) {
            return;
        }

        String bdf = record.getOrDefault("Slot", "").trim();
        String vendorField = record.getOrDefault("Vendor", "");
        String deviceField = record.getOrDefault("Device", "");
        String subsystemVendorField = record.getOrDefault("SVendor", "");
        String subsystemDeviceField = record.getOrDefault("SDevice", "");

        String vendorId = lastPciId(vendorField);
        String deviceId = lastPciId(deviceField);
        if (bdf.isBlank() || vendorId.isBlank() || deviceId.isBlank()) {
            return;
        }

        result.add(new RawGpu(
                bdf,
                stripLastPciId(className),
                stripLastPciId(vendorField),
                stripLastPciId(deviceField),
                stripLastPciId(subsystemVendorField),
                stripLastPciId(subsystemDeviceField),
                vendorId,
                deviceId,
                lastPciId(subsystemVendorField),
                lastPciId(subsystemDeviceField),
                record.getOrDefault("Driver", "").trim()));
    }

    private static Map<String, String> readNvidiaNames() {
        Map<String, String> result = new LinkedHashMap<>();
        String output = runCommand("nvidia-smi", "--query-gpu=pci.bus_id,name", "--format=csv,noheader,nounits");
        for (String line : output.split("\\R")) {
            int comma = line.indexOf(',');
            if (comma <= 0) {
                continue;
            }
            String bdf = normalizeBdf(line.substring(0, comma).trim());
            String name = line.substring(comma + 1).trim();
            if (!bdf.isBlank() && isUsefulName(name)) {
                result.put(bdf, name);
            }
        }
        return result;
    }

    private static Map<String, List<String>> readVulkanNames() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String output = runCommand("vulkaninfo", "--summary");
        Matcher blocks = VULKAN_GPU_BLOCK.matcher(output);

        while (blocks.find()) {
            String block = blocks.group();
            String vendorId = readVulkanField(block, "vendorID");
            String deviceId = readVulkanField(block, "deviceID");
            String deviceName = readVulkanField(block, "deviceName");
            if (vendorId.isBlank() || deviceId.isBlank() || !isUsefulName(deviceName)) {
                continue;
            }
            String key = normalizeHexId(vendorId) + ":" + normalizeHexId(deviceId);
            result.computeIfAbsent(key, unused -> new ArrayList<>()).add(deviceName);
        }
        return result;
    }

    private static String readVulkanField(String block, String field) {
        Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(field) + "\\s*=\\s*(.+?)\\s*$");
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim().replaceAll("^\"|\"$", "");
    }

    private static String readOpenGlRenderer() {
        String output = runCommand("glxinfo", "-B");
        for (String line : output.split("\\R")) {
            String prefix = "OpenGL renderer string:";
            int index = line.indexOf(prefix);
            if (index >= 0) {
                String renderer = line.substring(index + prefix.length()).trim();
                renderer = renderer.replaceFirst(
                        "\\s+\\((?:radeonsi|iris|crocus|nouveau|zink|llvmpipe|softpipe|swrast|NVIDIA).*\\)$", "");
                return renderer.trim();
            }
        }
        return "";
    }

    private static boolean isSpecificSubsystemName(String subsystemName, String deviceName) {
        if (!isUsefulName(subsystemName) || subsystemName.contains("/")) {
            return false;
        }
        if (subsystemName.equalsIgnoreCase(deviceName)) {
            return false;
        }
        return !subsystemName.matches("(?i)^(device|unknown device)\\s+[0-9a-f]{4}$");
    }

    private static boolean isUsefulName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.contains("llvmpipe")
                && !lower.contains("softpipe")
                && !lower.contains("software rasterizer")
                && !lower.equals("unknown")
                && !lower.contains("not found");
    }

    private static String shortVendorName(String vendorId, String fallback) {
        return switch (vendorId.toLowerCase(Locale.ROOT)) {
            case "1002" -> "AMD";
            case "10de" -> "NVIDIA";
            case "8086" -> "Intel";
            case "1af4" -> "Virtio";
            default -> fallback.isBlank() ? "Ismeretlen gyártó" : fallback;
        };
    }

    private static String lastPciId(String value) {
        Matcher matcher = PCI_ID.matcher(value);
        String id = "";
        while (matcher.find()) {
            id = matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return id;
    }

    private static String stripLastPciId(String value) {
        return value.replaceFirst("\\s+\\[[0-9a-fA-F]{4}]$", "").trim();
    }

    private static String normalizeHexId(String value) {
        String hex = value.trim().toLowerCase(Locale.ROOT).replaceFirst("^0x", "");
        try {
            return String.format(Locale.ROOT, "%04x", Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return hex;
        }
    }

    private static String normalizeBdf(String value) {
        Matcher matcher = Pattern.compile("(?i)^([0-9a-f]+):([0-9a-f]{2}):([0-9a-f]{2})\\.([0-7])$")
                .matcher(value.trim());
        if (!matcher.matches()) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
        try {
            long domain = Long.parseLong(matcher.group(1), 16);
            return String.format(Locale.ROOT, "%04x:%s:%s.%s", domain,
                    matcher.group(2).toLowerCase(Locale.ROOT),
                    matcher.group(3).toLowerCase(Locale.ROOT), matcher.group(4));
        } catch (NumberFormatException e) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static String runCommand(String... command) {
        Path outputFile = null;
        Process process = null;
        try {
            outputFile = Files.createTempFile("gpu-detect-", ".txt");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().put("LC_ALL", "C");
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());
            process = builder.start();

            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0) {
                return "";
            }
            return Files.readString(outputFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                }
            }
        }
    }
}