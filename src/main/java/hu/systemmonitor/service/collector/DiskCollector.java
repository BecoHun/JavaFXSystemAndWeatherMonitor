package hu.systemmonitor.service.collector;

import hu.systemmonitor.model.DiskPartitionRow;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DiskCollector {

    public static List<DiskPartitionRow> readDiskPartitions(OperatingSystem operatingSystem) {
        List<DiskPartitionRow> result = new ArrayList<>();
        try {
            FileSystem fileSystem = operatingSystem.getFileSystem();
            List<OSFileStore> fsList = fileSystem.getFileStores();
            for (OSFileStore fs : fsList) {
                long total = fs.getTotalSpace();
                long usable = fs.getUsableSpace();
                long used = Math.max(0, total - usable);
                double percent = total > 0 ? ((double) used / total) * 100.0 : 0.0;

                String name = fs.getName();
                String mount = fs.getMount();
                String type = fs.getType();

                if (total > 0) {
                    result.add(new DiskPartitionRow(
                            name,
                            mount,
                            type,
                            String.format(Locale.ROOT, "%.1f%%", percent),
                            formatBytes(used) + " / " + formatBytes(total)
                    ));
                }
            }
        } catch (Exception ignored) {}

        if (result.isEmpty()) {
            result.add(new DiskPartitionRow("N/A", "N/A", "N/A", "—", "Nincsenek elérhető adattároló adatok."));
        }
        return result;
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