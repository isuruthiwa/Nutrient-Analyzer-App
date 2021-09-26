package com.isuruthiwa.nutrientanalyzer;

public class ColorReading {

    Color red_read;
    Color green_read;
    Color blue_read;


    static class Color{
        int red;
        int green;
        int blue;

        @Override
        public String toString() {
            return "Color{" +
                    "red=" + red +
                    ", green=" + green +
                    ", blue=" + blue +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ColorReading{" +
                "red_read=" + red_read +
                ", green_read=" + green_read +
                ", blue_read=" + blue_read +
                '}';
    }
}
