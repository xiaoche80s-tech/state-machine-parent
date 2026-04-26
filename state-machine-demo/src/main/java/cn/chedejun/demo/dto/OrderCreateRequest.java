package cn.chedejun.demo.dto;

public record OrderCreateRequest(
    int stock,
    double amount,
    String address
) {}
