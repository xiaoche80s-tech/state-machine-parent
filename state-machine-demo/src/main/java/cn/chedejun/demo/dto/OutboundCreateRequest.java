package cn.chedejun.demo.dto;

public record OutboundCreateRequest(
    String warehouseCode,
    int totalQty,
    String carrierCode
) {}
