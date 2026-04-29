package cn.chedejun.demo.dto;

public class OrderCreateRequest {
    private final int stock;
    private final double amount;
    private final String address;

    public OrderCreateRequest(int stock, double amount, String address) {
        this.stock = stock;
        this.amount = amount;
        this.address = address;
    }

    public int getStock() {
        return stock;
    }

    public double getAmount() {
        return amount;
    }

    public String getAddress() {
        return address;
    }
}
