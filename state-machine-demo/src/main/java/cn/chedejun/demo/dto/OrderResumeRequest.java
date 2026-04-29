package cn.chedejun.demo.dto;

public class OrderResumeRequest {
    private final String businessId;
    private final String expectedCurrentState;
    private final String shippingAddress;

    public OrderResumeRequest(String businessId, String expectedCurrentState, String shippingAddress) {
        this.businessId = businessId;
        this.expectedCurrentState = expectedCurrentState;
        this.shippingAddress = shippingAddress;
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getExpectedCurrentState() {
        return expectedCurrentState;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }
}
