package cn.chedejun.demo.dto;

public record OrderResumeRequest(
    String businessId,
    String expectedCurrentState,
    String shippingAddress
) {}
