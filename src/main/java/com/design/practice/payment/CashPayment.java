package com.design.practice.payment;

public class CashPayment implements Payment {

    @Override
    public boolean pay(double amount) {
        System.out.println("Cash paid");
        return true;
    }
}
