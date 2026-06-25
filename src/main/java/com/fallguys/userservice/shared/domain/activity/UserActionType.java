package com.fallguys.userservice.shared.domain.activity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserActionType {

    WAREHOUSE_CREATED("창고 추가"),
    WAREHOUSE_UPDATED("창고 수정"),
    WAREHOUSE_STATUS_CHANGED("창고 상태"),
    STOCK_CREATED("재고 추가"),
    STOCK_ADJUSTED("재고 조정"),
    SAFETY_STOCK_UPDATED("안전재고 조정"),
    ITEM_CREATED("부품 등록"),
    ITEM_UPDATED("부품 수정"),
    ITEM_STATUS_CHANGED("부품 상태"),
    INBOUND_CREATED("입고"),
    OUTBOUND_CREATED("출고"),
    SALES_ORDER_UPDATED("발주 수정"),
    SALES_ORDER_STATUS_CHANGED("발주 상태"),
    PURCHASE_ORDER_REQUESTED("구매 요청"),
    PURCHASE_ORDER_UPDATED("구매 수정"),
    PURCHASE_ORDER_STATUS_CHANGED("구매 상태");

    private final String label;
}
