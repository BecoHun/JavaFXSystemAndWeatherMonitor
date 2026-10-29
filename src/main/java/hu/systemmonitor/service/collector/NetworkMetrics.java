package hu.systemmonitor.service.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NetworkMetrics {
    private long previousBytesRecv = 0;
    private long previousBytesSent = 0;
    private long previousTime = System.currentTimeMillis();
    private String cachedPublicIp = "Lekérdezés...";
    private final ObjectMapper mapper = new ObjectMapper();

    public void fetchIpAddresses(HardwareAbstractionLayer hardware) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // IP cím, ország és város lekérdezése az ipwho.is API segítségével
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://ipwho.is/"))
                    .header("User-Agent", "JavaFX-SystemMonitor/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                if (root.path("success").asBoolean(false)) {
                    String ip = root.path("ip").asText("");
                    String country = root.path("country").asText("");
                    String city = root.path("city").asText("");
                    if (!ip.isEmpty()) {
                        if (!country.isEmpty() && !city.isEmpty()) {
                            cachedPublicIp = String.format("%s (%s, %s)", ip, country, city);
                        } else if (!country.isEmpty()) {
                            cachedPublicIp = String.format("%s (%s)", ip, country);
                        } else {
                            cachedPublicIp = ip;
                        }
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}

        String[] services = {
                "https://api.ipify.org",
                "https://icanhazip.com",
                "https://ifconfig.me/ip"
        };

        for (String url : services) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "curl")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String ip = response.body().trim();
                    if (!ip.isEmpty() && ip.chars().filter(ch -> ch == '.').count() >= 3) {
                        cachedPublicIp = ip;
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        try {
            List<String> localIps = new ArrayList<>();
            for (NetworkIF net : hardware.getNetworkIFs()) {
                String[] ipv4s = net.getIPv4addr();
                if (ipv4s != null) {
                    for (String ip : ipv4s) {
                        if (ip != null && !ip.isBlank() && !ip.startsWith("127.")) {
                            localIps.add(ip);
                        }
                    }
                }
            }
            if (!localIps.isEmpty()) {
                cachedPublicIp = String.join(", ", localIps) + " (Helyi LAN)";
                return;
            }
        } catch (Exception ignored) {}

        cachedPublicIp = "Nem érhető el";
    }

    public String getCachedPublicIp() {
        return cachedPublicIp;
    }

    public NetworkData poll(HardwareAbstractionLayer hardware) {
        long currentBytesRecv = 0;
        long currentBytesSent = 0;
        for (NetworkIF net : hardware.getNetworkIFs()) {
            String name = net.getName().toLowerCase(Locale.ROOT);
            String displayName = net.getDisplayName().toLowerCase(Locale.ROOT);

            if (name.equals("lo") || name.startsWith("docker") || name.startsWith("br-") ||
                    name.startsWith("virbr") || name.startsWith("vnet") || name.startsWith("veth") ||
                    name.startsWith("tun") || name.startsWith("tap") || name.startsWith("dummy") ||
                    displayName.contains("loopback") || displayName.contains("virtual")) {
                continue;
            }

            net.updateAttributes();
            currentBytesRecv += net.getBytesRecv();
            currentBytesSent += net.getBytesSent();
        }

        long currentTime = System.currentTimeMillis();
        double timeElapsedSec = (currentTime - previousTime) / 1000.0;
        if (timeElapsedSec <= 0) timeElapsedSec = 1.0;

        double downloadSpeed = (currentBytesRecv - previousBytesRecv) / timeElapsedSec;
        double uploadSpeed = (currentBytesSent - previousBytesSent) / timeElapsedSec;

        previousBytesRecv = currentBytesRecv;
        previousBytesSent = currentBytesSent;
        previousTime = currentTime;

        return new NetworkData(downloadSpeed, uploadSpeed, currentBytesRecv, currentBytesSent);
    }

    public static class NetworkData {
        private final double downloadSpeed;
        private final double uploadSpeed;
        private final long totalReceived;
        private final long totalSent;

        public NetworkData(double downloadSpeed, double uploadSpeed, long totalReceived, long totalSent) {
            this.downloadSpeed = downloadSpeed;
            this.uploadSpeed = uploadSpeed;
            this.totalReceived = totalReceived;
            this.totalSent = totalSent;
        }

        public double getDownloadSpeed() { return downloadSpeed; }
        public double getUploadSpeed() { return uploadSpeed; }
        public long getTotalReceived() { return totalReceived; }
        public long getTotalSent() { return totalSent; }
    }
}