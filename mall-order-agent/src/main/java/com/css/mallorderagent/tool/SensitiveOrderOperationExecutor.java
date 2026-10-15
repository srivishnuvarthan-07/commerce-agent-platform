package com.css.mallorderagent.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.demo.DemoCapability;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.tool.client.OrderMcpToolClient;
import com.css.mallorderagent.tool.client.OrderMcpToolException;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes sensitive order operations (return / refund / exchange / cancel etc.)
 * after human approval, via MCP calls to mall-order-cmp-server.
 */
@Component
public class SensitiveOrderOperationExecutor {

    private static final Logger log = LoggerFactory.getLogger(SensitiveOrderOperationExecutor.class);

    private final OrderMcpToolClient orderMcpToolClient;

    public SensitiveOrderOperationExecutor(OrderMcpToolClient orderMcpToolClient) {
        this.orderMcpToolClient = orderMcpToolClient;
    }

    public SensitiveOperationResult execute(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        String toolResult = state.value(AgentGraphKeys.TOOL_RESULT, "");
        String userId = state.value(AgentGraphKeys.USER_ID, "").trim();
        String operation = HumanApprovalDetector.resolveOperationLabel(query);
        String orderId = HumanApprovalDetector.extractFirstOrderId(toolResult)
                .orElseGet(() -> OrderQueryParser.parseOrderId(query).orElse(null));

        if (AgentGraphSupport.hasCapabilityContext(state)) {
            boolean cancel = operation.contains("cancel");
            String required = (cancel ? DemoCapability.ORDER_CANCEL : DemoCapability.AFTER_SALES_CREATE).name();
            if (!AgentGraphSupport.hasCapability(state, required)) {
                return SensitiveOperationResult.failure(operation, orderId, userId,
                        "The current demo identity does not have permission to perform this sensitive order operation.");
            }
        }

        if (orderId == null || orderId.isBlank()) {
            return SensitiveOperationResult.failure(operation, null, userId,
                    "Could not identify an order number — unable to execute " + operation
                            + ". Please provide the order number and try again.");
        }
        if (userId.isBlank()) {
            return SensitiveOperationResult.failure(operation, orderId, userId,
                    "Could not identify the current user — unable to execute the sensitive order operation.");
        }

        log.info("Executing sensitive operation via MCP, operation={}, orderFingerprint={}",
                operation, TracePrivacy.fingerprint(orderId));
        try {
            String message = switch (operation) {
                case "cancel order", "cancel" -> executeCancel(orderId, userId);
                case "return"                 -> executeAfterSales(orderId, userId, "return");
                case "refund"                 -> executeAfterSales(orderId, userId, "refund");
                case "exchange"               -> executeAfterSales(orderId, userId, "exchange");
                case "address change"         -> executeAddressChange(orderId, userId);
                // Legacy Chinese labels (kept for backward compatibility)
                case "取消订单", "取消"         -> executeCancel(orderId, userId);
                case "退货"                   -> executeAfterSales(orderId, userId, "return");
                case "退款"                   -> executeAfterSales(orderId, userId, "refund");
                case "换货"                   -> executeAfterSales(orderId, userId, "exchange");
                case "修改收货地址"             -> executeAddressChange(orderId, userId);
                default                       -> executeAfterSales(orderId, userId, operation);
            };
            return SensitiveOperationResult.success(operation, orderId, userId, message);
        } catch (OrderMcpToolException e) {
            log.error("Sensitive operation failed via MCP, op={}, orderFingerprint={}",
                    operation, TracePrivacy.fingerprint(orderId), e);
            return SensitiveOperationResult.failure(operation, orderId, userId,
                    operation + " failed: the Order MCP service is unavailable (" + e.getMessage() + ")");
        }
    }

    private String executeCancel(String orderId, String userId) {
        boolean ok = orderMcpToolClient.cancelOrder(orderId, userId);
        if (ok) {
            return "Order " + orderId + " has been successfully cancelled.";
        }
        return "Failed to cancel order " + orderId + ". Please check whether the order status allows cancellation.";
    }

    private String executeAfterSales(String orderId, String userId, String operation) {
        return orderMcpToolClient.submitAfterSalesRequest(orderId, userId, operation);
    }

    private String executeAddressChange(String orderId, String userId) {
        return orderMcpToolClient.submitAddressChangeRequest(orderId, userId);
    }
}
