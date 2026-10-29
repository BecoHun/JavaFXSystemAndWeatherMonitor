package hu.systemmonitor.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class SensorRow {
    private final StringProperty name;
    private final StringProperty value;

    public SensorRow(String name, String value) {
        this.name = new SimpleStringProperty(name);
        this.value = new SimpleStringProperty(value);
    }

    public String getName() { return name.get(); }
    public StringProperty nameProperty() { return name; }

    public String getValue() { return value.get(); }
    public StringProperty valueProperty() { return value; }
}