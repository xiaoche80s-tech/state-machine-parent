package cn.chedejun.demo.dto;

/**
 * 收货单恢复请求
 */
public class ReceivingResumeRequest {
    private String businessId;
    private String expectedCurrentState;
    private int arrivedQty;
    private boolean firstArrived;
    private boolean allArrived;

    public ReceivingResumeRequest() {}

    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public String getExpectedCurrentState() { return expectedCurrentState; }
    public void setExpectedCurrentState(String expectedCurrentState) { this.expectedCurrentState = expectedCurrentState; }
    public int getArrivedQty() { return arrivedQty; }
    public void setArrivedQty(int arrivedQty) { this.arrivedQty = arrivedQty; }
    public boolean isFirstArrived() { return firstArrived; }
    public void setFirstArrived(boolean firstArrived) { this.firstArrived = firstArrived; }
    public boolean isAllArrived() { return allArrived; }
    public void setAllArrived(boolean allArrived) { this.allArrived = allArrived; }
}
