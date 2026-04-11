package com.design.practice.payment;

public class UpiPayment implements Payment{
    @Override
    public boolean pay(double amount) {
        System.out.println("UPI paid");
        return true;
    }
}
