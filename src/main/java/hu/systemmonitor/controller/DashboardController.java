package hu.systemmonitor.controller;

import hu.systemmonitor.model.DiskPartitionRow;
import hu.systemmonitor.model.SensorRow;
import hu.systemmonitor.service.SystemMonitorService;
import hu.systemmonitor.service.collector.GpuDetector;
import hu.systemmonitor.service.collector.WeatherCollector;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class DashboardController {

    @FXML private Label dateTimeLabel;
    @FXML private Label osInfoLabel;

    @FXML private Label motherboardLabel;
    @FXML private TableView<SensorRow> motherboardTableView;
    @FXML private TableColumn<SensorRow, String> motherboardNameCol;
    @FXML private TableColumn<SensorRow, String> motherboardValueCol;

    @FXML private Label cpuModelLabel;
    @FXML private Label cpuLabel;
    @FXML private Label cpuDetailsLabel;

    @FXML private Label gpuModelLabel;
    @FXML private TableView<SensorRow> gpuTableView;
    @FXML private TableColumn<SensorRow, String> gpuNameCol;
    @FXML private TableColumn<SensorRow, String> gpuValueCol;

    @FXML private Label memoryLabel;
    @FXML private Label swapLabel;

    @FXML private Label publicIpLabel;
    @FXML private Label netSpeedLabel;
    @FXML private Label netTotalLabel;

    @FXML private TableView<DiskPartitionRow> diskTableView;
    @FXML private TableColumn<DiskPartitionRow, String> diskNameCol;
    @FXML private TableColumn<DiskPartitionRow, String> diskMountCol;
    @FXML private TableColumn<DiskPartitionRow, String> diskTypeCol;
    @FXML private TableColumn<DiskPartitionRow, String> diskPercentCol;
    @FXML private TableColumn<DiskPartitionRow, String> diskSpaceCol;

    @FXML private Label weatherLabel;

    private SystemMonitorService systemMonitorService;

    @FXML
    public void initialize() {
        // Dinamikus oszlopszélesség-kezelés a különböző felbontású kijelzőkhöz
        motherboardTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        gpuTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        diskTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        gpuModelLabel.setText("Modell: " + GpuDetector.getGpuName());

        motherboardNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        motherboardValueCol.setCellValueFactory(new PropertyValueFactory<>("value"));

        gpuNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        gpuValueCol.setCellValueFactory(new PropertyValueFactory<>("value"));

        diskNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        diskMountCol.setCellValueFactory(new PropertyValueFactory<>("mount"));
        diskTypeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        diskPercentCol.setCellValueFactory(new PropertyValueFactory<>("usagePercent"));
        diskSpaceCol.setCellValueFactory(new PropertyValueFactory<>("spaceInfo"));

        systemMonitorService = new SystemMonitorService(data -> {
            dateTimeLabel.setText(data.getDateTimeInfo());
            osInfoLabel.setText(data.getOsInfo());

            motherboardLabel.setText(String.format("Modell: %s", data.getMotherboardInfo()));
            motherboardTableView.getItems().setAll(data.getMotherboardSensors());

            cpuModelLabel.setText(String.format("Típus: %s (%d mag, %d szál)",
                    data.getCpuName(), data.getPhysicalCores(), data.getLogicalCores()));
            cpuLabel.setText(String.format("Terhelés: %.2f %%", data.getCpuUsage()));
            cpuDetailsLabel.setText(String.format("Órajel: %s | Hőmérséklet: %s",
                    data.getCpuFrequency(), data.getCpuTemperature()));

            memoryLabel.setText(String.format("RAM használat: %.2f %% (%s / %s)",
                    data.getMemoryUsagePercent(), data.getUsedMemoryFormatted(), data.getTotalMemoryFormatted()));
            swapLabel.setText(String.format("SWAP használat: %.2f %% (%s / %s)",
                    data.getSwapUsagePercent(), data.getUsedSwapFormatted(), data.getTotalSwapFormatted()));

            gpuTableView.getItems().setAll(data.getGpuSensors());

            publicIpLabel.setText("Nyilvános IP: " + data.getPublicIp());
            netSpeedLabel.setText(String.format("Sebesség – Bejövő: %s | Kimenő: %s",
                    data.getDownloadSpeedFormatted(), data.getUploadSpeedFormatted()));
            netTotalLabel.setText(String.format("Összes adat – Letöltött: %s | Feltöltött: %s",
                    data.getTotalReceivedFormatted(), data.getTotalSentFormatted()));

            diskTableView.getItems().setAll(data.getDiskPartitions());
        });

        systemMonitorService.start();

        WeatherCollector weatherCollector = new WeatherCollector((weatherText, timeText) -> weatherLabel.setText(weatherText + " (" + timeText + ")"));
        weatherCollector.start();
    }

    @FXML
    public void refreshIp() {
        publicIpLabel.setText("Nyilvános IP: Frissítés...");
        if (systemMonitorService != null) {
            systemMonitorService.forceRefreshIpAsync(ip -> javafx.application.Platform.runLater(() -> publicIpLabel.setText("Nyilvános IP: " + ip)));
        }
    }
}