package com.design.practice.payment;

/**
 * [Strategy Pattern — Concrete Strategy: UPI Payment]
 *
 * Another interchangeable payment algorithm. In a real system this would
 * integrate with UPI APIs and could return false on network failure.
 */
public class UpiPayment implements Payment{
    @Override
    public boolean pay(double amount) {
        System.out.println("UPI paid");
        return true;
    }
}
