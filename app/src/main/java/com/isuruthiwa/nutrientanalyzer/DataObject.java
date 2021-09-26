package com.isuruthiwa.nutrientanalyzer;

import java.util.Date;

public class DataObject {
    Date ts;
    ColorReading colorReading;
    Double latitude;
    Double longitude;
    String nutrient;
    Double nutrientAmount;

    public DataObject(Date ts, ColorReading colorReading, Double latitude, Double longitude, String nutrient, Double nutrientAmount) {
        this.ts = ts;
        this.colorReading = colorReading;
        this.latitude = latitude;
        this.longitude = longitude;
        this.nutrient = nutrient;
        this.nutrientAmount = nutrientAmount;
    }
}
