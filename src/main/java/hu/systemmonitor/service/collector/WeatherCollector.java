package hu.systemmonitor.service.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class WeatherCollector {
    private static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=47.4979&longitude=19.0402&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m&timezone=Europe%2FBudapest";

    private final BiConsumer<String, String> updateCallback;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "weather-poller");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public WeatherCollector(BiConsumer<String, String> updateCallback) {
        this.updateCallback = updateCallback;
    }

    public void start() {
        fetch();
        executor.scheduleAtFixedRate(this::fetch, 0, 10, TimeUnit.MINUTES);
    }

    public void stop() {
        executor.shutdownNow();
    }

    private void fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "JavaFX-SystemMonitor/1.0")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            JsonNode current = mapper.readTree(response.body()).path("current");
            double temp = current.path("temperature_2m").asDouble(Double.NaN);
            double feels = current.path("apparent_temperature").asDouble(Double.NaN);
            int humidity = current.path("relative_humidity_2m").asInt(-1);
            double wind = current.path("wind_speed_10m").asDouble(Double.NaN);
            int code = current.path("weather_code").asInt(-1);
            String description = weatherDescription(code);

            String text = String.format(Locale.forLanguageTag("hu-HU"),
                    "Budapest  |  %.1f °C  |  Hőérzet: %.1f °C  |  Páratartalom: %d%%  |  Szél: %.1f km/h  |  %s",
                    temp, feels, humidity, wind, description);
            String timeText = "Frissítve: " + current.path("time").asText("ismeretlen időpont");

            if (updateCallback != null) {
                javafx.application.Platform.runLater(() -> updateCallback.accept(text, timeText));
            }
        } catch (Exception ex) {
            if (updateCallback != null) {
                javafx.application.Platform.runLater(() -> updateCallback.accept(
                        "Budapest időjárása nem érhető el: " + ex.getMessage(), "Ellenőrizd az internetkapcsolatot."));
            }
        }
    }

    private static String weatherDescription(int code) {
        return switch (code) {
            case 0 -> "Derült";
            case 1 -> "Többnyire derült";
            case 2 -> "Részben felhős";
            case 3 -> "Borult";
            case 45, 48 -> "Köd";
            case 51, 53, 55 -> "Szitálás";
            case 56, 57 -> "Ónos szitálás";
            case 61, 63, 65 -> "Eső";
            case 66, 67 -> "Ónos eső";
            case 71, 73, 75, 77 -> "Havazás";
            case 80, 81, 82 -> "Zápor";
            case 85, 86 -> "Hózápor";
            case 95 -> "Zivatar";
            case 96, 99 -> "Zivatar jégesővel";
            default -> "Időjárási kód: " + code;
        };
    }
}
