package cn.chedejun.demo.statemachine;

import cn.chedejun.statemachine.core.Context;

/**
 * 订单上下文
 */
public class OrderContext extends Context {
    private String orderId;
    private int stock;
    private double amount;
    private boolean paymentSuccess;
    private String shippingAddress;

    public OrderContext() {}

    public OrderContext(String orderId, int stock, double amount) {
        this.orderId = orderId;
        this.stock = stock;
        this.amount = amount;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public boolean isPaymentSuccess() { return paymentSuccess; }
    public void setPaymentSuccess(boolean paymentSuccess) { this.paymentSuccess = paymentSuccess; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
}
