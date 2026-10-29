package hu.systemmonitor.model;

public class DiskPartitionRow {
    private final String name;
    private final String mount;
    private final String type;
    private final String usagePercent;
    private final String spaceInfo;

    public DiskPartitionRow(String name, String mount, String type, String usagePercent, String spaceInfo) {
        this.name = name;
        this.mount = mount;
        this.type = type;
        this.usagePercent = usagePercent;
        this.spaceInfo = spaceInfo;
    }

    public String getName() { return name; }
    public String getMount() { return mount; }
    public String getType() { return type; }
    public String getUsagePercent() { return usagePercent; }
    public String getSpaceInfo() { return spaceInfo; }
}