package cn.chedejun.demo.dto;

public record OutboundResumeRequest(
    String businessId,
    String expectedCurrentState,
    String carrierCode
) {}
