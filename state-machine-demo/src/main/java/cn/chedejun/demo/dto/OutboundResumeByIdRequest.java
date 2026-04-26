package cn.chedejun.demo.dto;

public record OutboundResumeByIdRequest(
    String expectedCurrentState,
    String carrierCode
) {}
