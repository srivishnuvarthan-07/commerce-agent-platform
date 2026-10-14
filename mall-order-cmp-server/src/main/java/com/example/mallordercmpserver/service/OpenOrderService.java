package com.example.mallordercmpserver.service;

import com.example.mallordercmpserver.data.Order;
import com.example.mallordercmpserver.data.RefundEligibilityResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * @author: jyy
 * @Desc:
 **/
@Service
public class OpenOrderService {

    private final WebClient webClient;

    public OpenOrderService(WebClient.Builder webClientBuilder,
                            @Value("${mall-order.base-url:http://127.0.0.1:8081}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    @Tool(description = "Get the list of orders for a user by user ID")
    public List<Order> getOrdersByUserId(String userId) {
        List<Order> response = webClient.get()
                .uri("/orders/user/{userId}", userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Order>>() {
                })
                .block();
        return response != null ? response : List.of();
    }

    @Tool(description = "Get order details by order ID for the current user")
    public Order getOrderById(String orderId, String userId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/orders/{orderId}")
                        .queryParam("userId", userId)
                        .build(orderId))
                .retrieve()
                .bodyToMono(Order.class)
                .block();
    }

    @Tool(description = "Authoritative check of refund eligibility for the current user's order. Returns a 4-state result, reason codes, missing fields, and next action. The knowledge base must not override this result.")
    public String evaluateRefundEligibility(
            String orderId,
            String userId,
            @ToolParam(required = false, description = "NO_REASON/QUALITY_ISSUE/WRONG_ITEM/SHIPPING_DAMAGE/OTHER")
            String reasonType,
            @ToolParam(required = false, description = "Whether the customer has opened the package") Boolean customerOpened,
            @ToolParam(required = false, description = "Whether the customer has used the product") Boolean customerUsed,
            @ToolParam(required = false, description = "RESALABLE/NOT_RESALABLE") String conditionStatus,
            @ToolParam(required = false, description = "Description of the quality issue or problem") String reasonDescription,
            @ToolParam(required = false, description = "List of evidence file URLs") List<String> evidenceUrls) {
        RefundEligibilityResult result = webClient.post()
                .uri("/orders/{orderId}/refund-eligibility", orderId)
                .bodyValue(new RefundEligibilityRequest(
                        userId,
                        reasonType != null ? reasonType : "NO_REASON",
                        customerOpened,
                        customerUsed,
                        conditionStatus,
                        reasonDescription,
                        evidenceUrls != null ? evidenceUrls : List.of()))
                .retrieve()
                .bodyToMono(RefundEligibilityResult.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("mall-order returned an empty eligibility response"));
        return formatEligibility(result);
    }

    @Tool(description = "Cancel an order owned by the current user")
    public boolean cancelOrder(String orderId, String userId) {
        Boolean result = webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/orders/{orderId}/cancel")
                        .queryParam("userId", userId)
                        .build(orderId))
                .retrieve()
                .bodyToMono(Boolean.class)
                .block();
        return Boolean.TRUE.equals(result);
    }

    @Tool(description = "Submit a return, refund, or exchange after-sales request for the current user's order")
    public String submitAfterSalesRequest(String orderId, String userId, String operationType) {
        AfterSalesTicket ticket = createAfterSalesTicket(orderId, userId, operationType);
        return """
                Your %s request has been submitted successfully. Current status: %s.
                - Order ID: %s
                - Ticket ID: %s
                You can track the progress in "My Orders". Customer service will process it within 1 business day.
                """.formatted(ticket.operationType(), ticket.status(), ticket.orderId(), ticket.ticketId()).trim();
    }

    @Tool(description = "Submit a shipping address change request for the current user's order")
    public String submitAddressChangeRequest(String orderId, String userId) {
        AfterSalesTicket ticket = createAfterSalesTicket(orderId, userId, "Address Change");
        return """
                Your shipping address change request has been submitted.
                - Order ID: %s
                - Ticket ID: %s
                Please watch for a customer service notification to confirm the new address.
                """.formatted(ticket.orderId(), ticket.ticketId()).trim();
    }

    private AfterSalesTicket createAfterSalesTicket(String orderId, String userId, String operationType) {
        return webClient.post()
                .uri("/orders/{orderId}/after-sales", orderId)
                .bodyValue(new AfterSalesCommand(userId, operationType))
                .retrieve()
                .bodyToMono(AfterSalesTicket.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("mall-order returned an empty after-sales response"));
    }

    private record AfterSalesCommand(String userId, String operationType) {
    }

    private record RefundEligibilityRequest(
            String userId,
            String reasonType,
            Boolean customerOpened,
            Boolean customerUsed,
            String conditionStatus,
            String reasonDescription,
            List<String> evidenceUrls) {
    }

    private record AfterSalesTicket(String ticketId, String orderId, String userId,
                                    String operationType, String status) {
    }

    private static String formatEligibility(RefundEligibilityResult result) {
        String amount = result.getRefundableAmount() != null ? result.getRefundableAmount().toPlainString() : "-";
        String operation = result.getOperationType() != null ? result.getOperationType() : "-";
        return """
                [REFUND ELIGIBILITY RESULT]
                Order ID: %s
                Decision: %s
                Operation Type: %s
                Reason Codes: %s
                Missing Fields: %s
                Next Action: %s
                Refundable Amount: %s
                Policy Version: %s
                Note: This result is calculated by the order rules service from live database facts. The knowledge base and LLM must not override this result.
                """.formatted(
                result.getOrderId(),
                result.getDecision(),
                operation,
                result.getReasonCodes() != null ? result.getReasonCodes() : List.of(),
                result.getMissingFields() != null ? result.getMissingFields() : List.of(),
                result.getNextAction(),
                amount,
                result.getPolicyVersion()).trim();
    }
}
