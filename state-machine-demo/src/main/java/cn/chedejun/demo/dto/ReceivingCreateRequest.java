package cn.chedejun.demo.dto;

/**
 * 收货单创建请求
 */
public class ReceivingCreateRequest {
    private int totalQty;

    public ReceivingCreateRequest() {}

    public ReceivingCreateRequest(int totalQty) {
        this.totalQty = totalQty;
    }

    public int getTotalQty() { return totalQty; }
    public void setTotalQty(int totalQty) { this.totalQty = totalQty; }
}
