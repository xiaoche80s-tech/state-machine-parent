package cn.chedejun.demo.statemachine;

import cn.chedejun.statemachine.core.Context;

/**
 * 出库单上下文
 */
public class OutboundContext extends Context {
    private String outboundNo;
    private String warehouseCode;
    private int totalQty;
    private boolean picked;
    private boolean packed;
    private boolean shipped;
    private String carrierCode;
    private String trackingNo;

    public OutboundContext() {}

    public OutboundContext(String outboundNo, String warehouseCode, int totalQty) {
        this.outboundNo = outboundNo;
        this.warehouseCode = warehouseCode;
        this.totalQty = totalQty;
    }

    public String getOutboundNo() { return outboundNo; }
    public void setOutboundNo(String outboundNo) { this.outboundNo = outboundNo; }
    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
    public int getTotalQty() { return totalQty; }
    public void setTotalQty(int totalQty) { this.totalQty = totalQty; }
    public boolean isPicked() { return picked; }
    public void setPicked(boolean picked) { this.picked = picked; }
    public boolean isPacked() { return packed; }
    public void setPacked(boolean packed) { this.packed = packed; }
    public boolean isShipped() { return shipped; }
    public void setShipped(boolean shipped) { this.shipped = shipped; }
    public String getCarrierCode() { return carrierCode; }
    public void setCarrierCode(String carrierCode) { this.carrierCode = carrierCode; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
}
