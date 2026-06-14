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
    private boolean routeFailed;
    private UserInfo user;

    public OrderContext() {}

    public OrderContext(String orderId, int stock, double amount) {
        this.orderId = orderId;
        this.stock = stock;
        this.amount = amount;
    }

    public static class UserInfo {
        private String userName;
        private String userPhone;

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getUserPhone() { return userPhone; }
        public void setUserPhone(String userPhone) { this.userPhone = userPhone; }
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
    public boolean isRouteFailed() { return routeFailed; }
    public void setRouteFailed(boolean routeFailed) { this.routeFailed = routeFailed; }
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
}
