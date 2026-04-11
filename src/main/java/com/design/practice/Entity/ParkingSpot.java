package com.design.practice.Entity;

public class ParkingSpot {

    private String spotId;
    private boolean isFree = true;
    public ParkingSpot(String spotId) {
        this.spotId = spotId;
    }

    public boolean isSpotFree() {
        return isFree;
    }
    public void occupySpot() {
        isFree = false;
    }
    public void releaseSpot() {
        isFree = true;
    }

    public String getSpotId() {
        return spotId;
    }
}
