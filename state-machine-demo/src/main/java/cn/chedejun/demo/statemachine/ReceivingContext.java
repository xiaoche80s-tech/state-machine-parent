package cn.chedejun.demo.statemachine;

import cn.chedejun.statemachine.core.Context;

/**
 * 收货上下文
 */
public class ReceivingContext extends Context {
    private String receivingNo;
    private int totalQty;
    private int arrivedQty;
    private boolean firstArrived;
    private boolean allArrived;
    private boolean qualityChecked;

    public ReceivingContext() {}

    public ReceivingContext(String receivingNo, int totalQty) {
        this.receivingNo = receivingNo;
        this.totalQty = totalQty;
    }

    public String getReceivingNo() { return receivingNo; }
    public void setReceivingNo(String receivingNo) { this.receivingNo = receivingNo; }
    public int getTotalQty() { return totalQty; }
    public void setTotalQty(int totalQty) { this.totalQty = totalQty; }
    public int getArrivedQty() { return arrivedQty; }
    public void setArrivedQty(int arrivedQty) { this.arrivedQty = arrivedQty; }
    public boolean isFirstArrived() { return firstArrived; }
    public void setFirstArrived(boolean firstArrived) { this.firstArrived = firstArrived; }
    public boolean isAllArrived() { return allArrived; }
    public void setAllArrived(boolean allArrived) { this.allArrived = allArrived; }
    public boolean isQualityChecked() { return qualityChecked; }
    public void setQualityChecked(boolean qualityChecked) { this.qualityChecked = qualityChecked; }
}
