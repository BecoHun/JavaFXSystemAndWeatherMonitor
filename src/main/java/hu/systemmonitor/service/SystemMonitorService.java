package hu.systemmonitor.service;

import hu.systemmonitor.model.DiskPartitionRow;
import hu.systemmonitor.model.SensorRow;
import hu.systemmonitor.service.collector.DateTimeCollector;
import hu.systemmonitor.service.collector.DiskCollector;
import hu.systemmonitor.service.collector.GpuSensorCollector;
import hu.systemmonitor.service.collector.MotherboardSensorCollector;
import hu.systemmonitor.service.collector.NetworkMetrics;
import hu.systemmonitor.service.collector.OsInfoCollector;
import hu.systemmonitor.service.collector.GpuDetector;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.Sensors;
import oshi.software.os.OperatingSystem;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SystemMonitorService {
    private final Consumer<SystemMetricsData> updateCallback;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "system-monitor-poller");
        t.setDaemon(true);
        return t;
    });
    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
    private final ComputerSystem computerSystem = hardware.getComputerSystem();
    private final CentralProcessor cpu = hardware.getProcessor();
    private final GlobalMemory memory = hardware.getMemory();
    private final Sensors sensors = hardware.getSensors();
    private long[] previousTicks = cpu.getSystemCpuLoadTicks();

    private final NetworkMetrics networkMetrics = new NetworkMetrics();

    private static final String STATIC_GPU_NAME = resolveStaticGpuName();

    public static String getStaticGpuName() {
        return STATIC_GPU_NAME;
    }

    private static String resolveStaticGpuName() {
        return GpuDetector.getGpuName();
    }

    public SystemMonitorService(Consumer<SystemMetricsData> updateCallback) {
        this.updateCallback = updateCallback;
        new Thread(() -> networkMetrics.fetchIpAddresses(hardware)).start();
    }

    public void forceRefreshIpAsync(Consumer<String> onIpUpdated) {
        new Thread(() -> {
            networkMetrics.fetchIpAddresses(hardware);
            if (onIpUpdated != null) {
                onIpUpdated.accept(networkMetrics.getCachedPublicIp());
            }
        }).start();
    }

    public void start() {
        poll();
        executor.scheduleAtFixedRate(this::poll, 0, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    private void poll() {
        try {
            String motherboardInfo = MotherboardSensorCollector.getMotherboardInfo();

            double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(previousTicks) * 100.0;
            previousTicks = cpu.getSystemCpuLoadTicks();
            String cpuName = cpu.getProcessorIdentifier().getName();
            int physicalCores = cpu.getPhysicalProcessorCount();
            int logicalCores = cpu.getLogicalProcessorCount();

            long[] frequencies = cpu.getCurrentFreq();
            double avgFrequency = Arrays.stream(frequencies).filter(f -> f > 0).average().orElse(0.0);
            if (avgFrequency == 0) {
                avgFrequency = cpu.getMaxFreq();
            }

            double cpuTemp = sensors.getCpuTemperature();
            double finalCpuLoad = Double.isFinite(cpuLoad) ? Math.clamp(cpuLoad, 0, 100) : 0;

            String frequencyText = avgFrequency > 0 ? String.format(Locale.ROOT, "%.2f GHz", avgFrequency / 1_000_000_000.0) : "Ismeretlen";
            String tempText = cpuTemp > 0 ? String.format(Locale.ROOT, "%.1f °C", cpuTemp) : "Nem érhető el";

            long totalRam = memory.getTotal();
            long availableRam = memory.getAvailable();
            long usedRam = Math.max(0, totalRam - availableRam);
            double memoryUsagePercent = ((double) usedRam / totalRam) * 100.0;

            double totalGB = totalRam / 1_000_000_000.0;
            double usedGB = usedRam / 1_000_000_000.0;
            String totalMemoryFormatted = String.format(Locale.ROOT, "%.1f GB", totalGB);
            String usedMemoryFormatted = String.format(Locale.ROOT, "%.1f GB", usedGB);

            long totalSwap = memory.getVirtualMemory().getSwapTotal();
            long usedSwap = memory.getVirtualMemory().getSwapUsed();
            double swapUsagePercent = totalSwap > 0 ? ((double) usedSwap / totalSwap) * 100.0 : 0.0;

            double totalSwapGB = totalSwap / 1_000_000_000.0;
            double usedSwapGB = usedSwap / 1_000_000_000.0;
            String totalSwapFormatted = String.format(Locale.ROOT, "%.1f GB", totalSwapGB);
            String usedSwapFormatted = String.format(Locale.ROOT, "%.1f GB", usedSwapGB);

            NetworkMetrics.NetworkData netData = networkMetrics.poll(hardware);

            List<SensorRow> motherboardSensors = MotherboardSensorCollector.readSensors();
            List<SensorRow> gpuSensors = GpuSensorCollector.readSensors();
            List<DiskPartitionRow> diskPartitions = DiskCollector.readDiskPartitions(operatingSystem);

            long uptimeSeconds = operatingSystem.getSystemUptime();
            String uptimeFormatted = formatUptime(uptimeSeconds);
            String fullOsInfo = OsInfoCollector.getBaseOsInfo() + " | Futási idő: " + uptimeFormatted;

            String dateTimeInfo = DateTimeCollector.getDateTimeInfo();

            if (updateCallback != null) {
                SystemMetricsData data = new SystemMetricsData(
                        dateTimeInfo,
                        fullOsInfo,
                        motherboardInfo, cpuName, physicalCores, logicalCores, finalCpuLoad,
                        frequencyText, tempText, memoryUsagePercent,
                        usedMemoryFormatted, totalMemoryFormatted,
                        swapUsagePercent, usedSwapFormatted, totalSwapFormatted,
                        motherboardSensors, gpuSensors,
                        networkMetrics.getCachedPublicIp(), netData.getDownloadSpeed(), netData.getUploadSpeed(),
                        netData.getTotalReceived(), netData.getTotalSent(),
                        diskPartitions
                );
                javafx.application.Platform.runLater(() -> updateCallback.accept(data));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static String formatUptime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.ROOT, "%d óra %d perc %d mp", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format(Locale.ROOT, "%d perc %d mp", minutes, secs);
        } else {
            return String.format(Locale.ROOT, "%d mp", secs);
        }
    }

    public static class SystemMetricsData {
        private final String dateTimeInfo;
        private final String osInfo;
        private final String motherboardInfo;
        private final String cpuName;
        private final int physicalCores;
        private final int logicalCores;
        private final double cpuUsage;
        private final String cpuFrequency;
        private final String cpuTemperature;
        private final double memoryUsagePercent;
        private final String usedMemoryFormatted;
        private final String totalMemoryFormatted;
        private final double swapUsagePercent;
        private final String usedSwapFormatted;
        private final String totalSwapFormatted;
        private final List<SensorRow> motherboardSensors;
        private final List<SensorRow> gpuSensors;

        private final String publicIp;
        private final double downloadSpeed;
        private final double uploadSpeed;
        private final long totalReceived;
        private final long totalSent;
        private final List<DiskPartitionRow> diskPartitions;

        public SystemMetricsData(String dateTimeInfo, String osInfo, String motherboardInfo, String cpuName, int physicalCores, int logicalCores, double cpuUsage,
                                 String cpuFrequency, String cpuTemperature, double memoryUsagePercent,
                                 String usedMemoryFormatted, String totalMemoryFormatted,
                                 double swapUsagePercent, String usedSwapFormatted, String totalSwapFormatted,
                                 List<SensorRow> motherboardSensors, List<SensorRow> gpuSensors,
                                 String publicIp, double downloadSpeed, double uploadSpeed, long totalReceived, long totalSent,
                                 List<DiskPartitionRow> diskPartitions) {
            this.dateTimeInfo = dateTimeInfo;
            this.osInfo = osInfo;
            this.motherboardInfo = motherboardInfo;
            this.cpuName = cpuName;
            this.physicalCores = physicalCores;
            this.logicalCores = logicalCores;
            this.cpuUsage = cpuUsage;
            this.cpuFrequency = cpuFrequency;
            this.cpuTemperature = cpuTemperature;
            this.memoryUsagePercent = memoryUsagePercent;
            this.usedMemoryFormatted = usedMemoryFormatted;
            this.totalMemoryFormatted = totalMemoryFormatted;
            this.swapUsagePercent = swapUsagePercent;
            this.usedSwapFormatted = usedSwapFormatted;
            this.totalSwapFormatted = totalSwapFormatted;
            this.motherboardSensors = motherboardSensors;
            this.gpuSensors = gpuSensors;
            this.publicIp = publicIp;
            this.downloadSpeed = downloadSpeed;
            this.uploadSpeed = uploadSpeed;
            this.totalReceived = totalReceived;
            this.totalSent = totalSent;
            this.diskPartitions = diskPartitions;
        }

        public String getDateTimeInfo() { return dateTimeInfo; }
        public String getOsInfo() { return osInfo; }
        public String getMotherboardInfo() { return motherboardInfo; }
        public String getCpuName() { return cpuName; }
        public int getPhysicalCores() { return physicalCores; }
        public int getLogicalCores() { return logicalCores; }
        public double getCpuUsage() { return cpuUsage; }
        public String getCpuFrequency() { return cpuFrequency; }
        public String getCpuTemperature() { return cpuTemperature; }
        public double getMemoryUsagePercent() { return memoryUsagePercent; }
        public String getUsedMemoryFormatted() { return usedMemoryFormatted; }
        public String getTotalMemoryFormatted() { return totalMemoryFormatted; }
        public double getSwapUsagePercent() { return swapUsagePercent; }
        public String getUsedSwapFormatted() { return usedSwapFormatted; }
        public String getTotalSwapFormatted() { return totalSwapFormatted; }
        public List<SensorRow> getMotherboardSensors() { return motherboardSensors; }
        public List<SensorRow> getGpuSensors() { return gpuSensors; }

        public String getPublicIp() { return publicIp; }
        public String getDownloadSpeedFormatted() { return formatSpeed(downloadSpeed); }
        public String getUploadSpeedFormatted() { return formatSpeed(uploadSpeed); }
        public String getTotalReceivedFormatted() { return formatBytes(totalReceived); }
        public String getTotalSentFormatted() { return formatBytes(totalSent); }
        public List<DiskPartitionRow> getDiskPartitions() { return diskPartitions; }

        private String formatBytes(long bytes) {
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

        private String formatSpeed(double bytesPerSec) {
            if (bytesPerSec < 1024) return String.format(Locale.ROOT, "%.1f B/s", bytesPerSec);
            if (bytesPerSec < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB/s", bytesPerSec / 1024.0);
            return String.format(Locale.ROOT, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0));
        }
    }
}