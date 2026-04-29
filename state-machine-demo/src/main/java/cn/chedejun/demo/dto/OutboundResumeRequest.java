package cn.chedejun.demo.dto;

public class OutboundResumeRequest {
    private final String businessId;
    private final String expectedCurrentState;
    private final String carrierCode;

    public OutboundResumeRequest(String businessId, String expectedCurrentState, String carrierCode) {
        this.businessId = businessId;
        this.expectedCurrentState = expectedCurrentState;
        this.carrierCode = carrierCode;
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getExpectedCurrentState() {
        return expectedCurrentState;
    }

    public String getCarrierCode() {
        return carrierCode;
    }
}
