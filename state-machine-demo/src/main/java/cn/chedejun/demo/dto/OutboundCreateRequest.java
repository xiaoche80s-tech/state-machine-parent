package cn.chedejun.demo.dto;

public class OutboundCreateRequest {
    private final String warehouseCode;
    private final int totalQty;
    private final String carrierCode;

    public OutboundCreateRequest(String warehouseCode, int totalQty, String carrierCode) {
        this.warehouseCode = warehouseCode;
        this.totalQty = totalQty;
        this.carrierCode = carrierCode;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public int getTotalQty() {
        return totalQty;
    }

    public String getCarrierCode() {
        return carrierCode;
    }
}
