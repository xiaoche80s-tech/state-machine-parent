package cn.chedejun.demo.dto;

public record OrderResumeByIdRequest(
    String expectedCurrentState,
    String shippingAddress
) {}
