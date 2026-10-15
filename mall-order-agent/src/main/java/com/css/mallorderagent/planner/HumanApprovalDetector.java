package com.css.mallorderagent.planner;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Determines whether the current query/answer involves a sensitive order operation
 * that requires human-in-the-loop review.
 * <p>
 * Informational queries (e.g. "what is the refund policy?") are not flagged;
 * action/confirmation queries (e.g. "I want a refund", "confirm cancellation") are.
 * Both English and Chinese keywords are recognised.
 * </p>
 */
public final class HumanApprovalDetector {

    // -------------------------------------------------------------------------
    // Keyword lists (English + Chinese)
    // -------------------------------------------------------------------------

    private static final List<String> INFORMATIONAL_MARKERS = List.of(
            // English
            "policy", "rule", "process", "how to", "how do", "what is", "can i",
            "what are", "how many", "explain", "tell me", "show me", "check",
            "status", "progress", "when will", "how long", "track",
            // Chinese
            "规则", "政策", "流程", "时效", "是什么", "什么是", "怎么回事",
            "怎么", "如何", "能不能", "可以吗", "有哪些", "多少", "介绍", "说明",
            "查询", "查一下", "查看", "进度", "状态", "到账", "到哪", "什么时候", "多久");

    private static final List<String> ACTION_INTENT_MARKERS = List.of(
            // English
            "i want to", "please", "help me", "confirm", "apply", "immediately",
            "go pay", "submit",
            // Chinese
            "我要", "帮我", "请帮", "确认", "申请", "立即", "马上", "去付", "去支付");

    private static final List<String> STANDALONE_SENSITIVE_TERMS = List.of(
            // English
            "return", "refund", "exchange", "change address", "cancel order",
            "cancel", "payment", "pay",
            // Chinese
            "退货", "退款", "换货", "改地址", "取消订单", "取消", "付款", "支付");

    private static final List<String> DANGEROUS_QUERY_MARKERS = List.of(
            // English
            "apply for return", "apply for refund", "i want to return", "help me return",
            "confirm return", "confirm refund", "confirm payment",
            "cancel order", "delete order", "delete account",
            "go to payment", "pay now", "pay immediately",
            "apply for exchange", "i want to exchange", "help me exchange", "confirm exchange",
            "change address", "modify address", "update address", "change delivery address",
            "confirm address change", "modify shipping address",
            // Chinese
            "申请退货", "申请退款", "我要退", "帮我退", "确认退货", "确认退款",
            "确认付款", "确认支付", "取消订单", "删除订单", "删除账号",
            "去付款", "去支付", "立即支付", "立即付款", "跳转付款", "马上付",
            "申请换货", "我要换货", "帮我换货", "确认换货",
            "改地址", "修改地址", "变更地址", "更换地址", "确认改地址", "修改收货地址", "改收货地址");

    // Patterns that detect confirmation questions in assistant answers (English + Chinese)
    private static final Pattern CONFIRM_QUESTION = Pattern.compile(
            "(?i)confirm.{0,30}(refund|return|payment|cancel|delete|exchange|address).{0,20}\\??" +
            "|确认.{0,25}(退|退款|退货|付|支付|付款|取消|删除|换货|地址).{0,15}吗[？?]?");
    private static final Pattern CONFIRM_SHORT = Pattern.compile(
            "(?i)(confirm|would you like to).{0,20}(refund|return|exchange|cancel|pay|address).{0,10}\\??" +
            "|确认(?:申请)?(?:退|退款|退货|换货|付|支付|付款|改地址|取消).{0,6}吗[？?]?");
    private static final Pattern RISK_CONFIRM = Pattern.compile(
            "(?i)(are you sure|do you confirm).{0,15}(delete order|cancel.{0,5}order|refund|return|exchange|address)" +
            "|(确定|是否).{0,12}(删除订单|取消.{0,3}订单|申请退|确认退|确认付|换货|收货地址|修改地址)");

    private static final Pattern ORDER_ID = Pattern.compile("ORD\\w+");
    private static final Pattern PRODUCT_LINE = Pattern.compile("-\\s*(.+?)\\s+x\\d+");

    /** User's reply intent to a confirmation prompt. */
    public enum ConfirmationIntent {
        CONFIRM, CANCEL, UNKNOWN
    }

    private HumanApprovalDetector() {
    }

    /** Resolves the operation label from user input (English or Chinese). */
    public static String resolveOperationLabel(String query) {
        if (query == null || query.isBlank()) {
            return "this operation";
        }
        String text = query.trim().toLowerCase();
        if (containsAny(text, "refund", "退款")) {
            return "refund";
        }
        if (containsAny(text, "return", "退货")) {
            return "return";
        }
        if (containsAny(text, "exchange", "换货")) {
            return "exchange";
        }
        if (containsAny(text, "address", "地址", "收货地址")) {
            return "address change";
        }
        if (containsAny(text, "cancel", "取消")) {
            return "cancel order";
        }
        if (containsAny(text, "delete", "删除")) {
            return "delete";
        }
        if (containsAny(text, "pay", "payment", "付", "支付", "付款")) {
            return "payment";
        }
        return "this operation";
    }

    /** Builds a human-readable confirmation prompt for a sensitive order operation. */
    public static String buildDangerousOrderConfirmation(String query, String toolResult, String ragContext) {
        String operation = resolveOperationLabel(query);
        String orderId = extractFirstOrderId(toolResult).orElse("the above order");
        String productName = extractFirstProductName(toolResult).orElse(null);
        StringBuilder sb = new StringBuilder();
        if (productName != null && !productName.isBlank()) {
            if ("return".equals(operation)) {
                sb.append("Are you sure you want to return the item \"").append(productName)
                        .append("\" from order ").append(orderId).append("?");
            } else {
                sb.append("Are you sure you want to submit a ").append(operation)
                        .append(" request for \"").append(productName)
                        .append("\" (order ").append(orderId).append(")?");
            }
        } else if ("return".equals(operation)) {
            sb.append("Are you sure you want to return the items from order ").append(orderId).append("?");
        } else {
            sb.append("Are you sure you want to submit a ").append(operation)
                    .append(" request for order ").append(orderId).append("?");
        }
        sb.append("\n\nReply **confirm** to proceed, or **cancel** to abort.");
        if (ragContext != null && !ragContext.isBlank()) {
            sb.append("\n\nRelevant policy:\n").append(trimContext(ragContext));
        }
        return sb.toString().trim();
    }

    /** Parses the user's reply to a confirmation prompt. */
    public static ConfirmationIntent parseUserConfirmationIntent(String text) {
        if (text == null || text.isBlank()) {
            return ConfirmationIntent.UNKNOWN;
        }
        String normalized = text.trim().toLowerCase().replaceAll("[？?。！!，,、\\s]", "");
        if (isCancelReply(normalized)) {
            return ConfirmationIntent.CANCEL;
        }
        if (isConfirmReply(normalized)) {
            return ConfirmationIntent.CONFIRM;
        }
        return ConfirmationIntent.UNKNOWN;
    }

    public static String buildCancelMessage(String query) {
        return "Got it — your " + resolveOperationLabel(query) + " request has been cancelled.";
    }

    /** @deprecated Use {@link #buildDangerousOrderConfirmation} */
    public static String buildConfirmationFallback(String query, String toolResult) {
        return buildDangerousOrderConfirmation(query, toolResult, null);
    }

    public static boolean isDangerousOrderOp(String planStrategy) {
        return "DANGEROUS_ORDER_OP".equals(planStrategy);
    }

    private static String trimContext(String ragContext) {
        String trimmed = ragContext.trim();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 500) + "...";
    }

    public static Optional<String> extractFirstOrderId(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = ORDER_ID.matcher(toolResult);
        if (matcher.find()) {
            return Optional.of(matcher.group());
        }
        return Optional.empty();
    }

    public static Optional<String> extractFirstProductName(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PRODUCT_LINE.matcher(toolResult);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).trim());
        }
        return Optional.empty();
    }

    private static boolean isConfirmReply(String normalized) {
        // English
        if (normalized.equals("yes") || normalized.equals("confirm") || normalized.equals("ok")
                || normalized.equals("okay") || normalized.equals("sure") || normalized.equals("proceed")
                || normalized.equals("yep") || normalized.equals("yup") || normalized.equals("agreed")) {
            return true;
        }
        if (normalized.endsWith("confirm") || normalized.endsWith("proceed")
                || normalized.contains("yes,confirm") || normalized.contains("confirmit")) {
            return true;
        }
        // Chinese
        if (normalized.equals("确认") || normalized.equals("确定") || normalized.equals("同意")
                || normalized.equals("是的") || normalized.equals("是") || normalized.equals("好的")
                || normalized.equals("可以") || normalized.equals("执行") || normalized.equals("确认执行")) {
            return true;
        }
        return normalized.endsWith("确认") || normalized.endsWith("确定")
                || normalized.contains("确认执行") || normalized.contains("确定执行");
    }

    private static boolean isCancelReply(String normalized) {
        // English
        if (normalized.equals("no") || normalized.equals("cancel") || normalized.equals("abort")
                || normalized.equals("stop") || normalized.equals("nope") || normalized.equals("nah")
                || normalized.equals("nevermind") || normalized.equals("never mind")
                || normalized.equals("donotproceed") || normalized.equals("dontproceed")) {
            return true;
        }
        if (normalized.contains("cancel") || normalized.contains("abort") || normalized.contains("donot")) {
            return true;
        }
        // Chinese
        if (normalized.equals("取消") || normalized.equals("不要") || normalized.equals("算了")
                || normalized.equals("不用") || normalized.equals("否") || normalized.equals("不")
                || normalized.equals("放弃") || normalized.equals("不确认")) {
            return true;
        }
        return normalized.contains("取消") || normalized.contains("不要了") || normalized.contains("算了");
    }

    public static boolean queryRequiresApproval(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String text = query.trim();
        if (isInformationalOnly(text)) {
            return false;
        }
        if (isQueryOrStatusIntent(text)) {
            return false;
        }
        if (isStandaloneSensitiveIntent(text)) {
            return true;
        }
        return DANGEROUS_QUERY_MARKERS.stream().anyMatch(m -> text.toLowerCase().contains(m.toLowerCase()));
    }

    /** Returns true when the query likely needs order context attached. */
    public static boolean shouldAttachOrderContext(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String text = query.trim().toLowerCase();
        if (containsAny(text, "order", "订单")) {
            return true;
        }
        return containsAny(text, "return", "refund", "exchange", "cancel order", "cancel",
                "退货", "退款", "换货", "取消订单", "取消");
    }

    public static boolean answerRequiresApproval(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String text = answer.trim();
        return CONFIRM_QUESTION.matcher(text).find()
                || CONFIRM_SHORT.matcher(text).find()
                || RISK_CONFIRM.matcher(text).find();
    }

    public static boolean requiresApproval(String query, String answer) {
        return queryRequiresApproval(query) || answerRequiresApproval(answer);
    }

    /** Combines the global switch, planner flag, and heuristics to decide whether to enter review. */
    public static boolean requiresReview(boolean globalEnabled,
                                         boolean planRequires,
                                         String query,
                                         String answer) {
        if (!globalEnabled) {
            return false;
        }
        return planRequires || queryRequiresApproval(query) || answerRequiresApproval(answer);
    }

    public static String resolveReason(String query, String answer) {
        if (queryRequiresApproval(query)) {
            return resolveQueryReason(query);
        }
        if (answerRequiresApproval(answer)) {
            return "The assistant's reply contains a sensitive operation confirmation — please review before sending";
        }
        return "Please review the assistant's reply";
    }

    private static String resolveQueryReason(String query) {
        String text = query.trim().toLowerCase();
        if (containsAny(text, "return", "refund", "退", "退款", "退货")) {
            return "This involves a return/refund operation — please review the assistant's reply before sending";
        }
        if (containsAny(text, "pay", "payment", "付", "支付", "付款")) {
            return "This involves a payment operation — please review the assistant's reply before sending";
        }
        if (containsAny(text, "cancel", "取消")) {
            return "This involves an order cancellation — please review the assistant's reply before sending";
        }
        if (containsAny(text, "delete", "删除")) {
            return "This involves a deletion operation — please review the assistant's reply before sending";
        }
        if (containsAny(text, "exchange", "换货")) {
            return "This involves an exchange operation — please review the assistant's reply before sending";
        }
        if (containsAny(text, "address", "地址", "收货地址")) {
            return "This involves a delivery address change — please review the assistant's reply before sending";
        }
        return "This involves a sensitive operation — please review the assistant's reply before sending";
    }

    private static boolean isInformationalOnly(String text) {
        String lower = text.toLowerCase();
        boolean hasInfoMarker = INFORMATIONAL_MARKERS.stream()
                .anyMatch(m -> lower.contains(m.toLowerCase()));
        boolean hasExplicitAction = ACTION_INTENT_MARKERS.stream()
                .anyMatch(m -> lower.contains(m.toLowerCase()));
        return hasInfoMarker && !hasExplicitAction;
    }

    /**
     * Detects status/progress queries like "check refund status" or "where is my order" —
     * not an action, so no approval needed.
     */
    private static boolean isQueryOrStatusIntent(String text) {
        if (hasActionIntentMarkers(text)) {
            return false;
        }
        String lower = text.toLowerCase();
        boolean hasQueryIntent = containsAny(lower,
                "check", "look up", "find", "show me", "view", "track", "where",
                "查询", "查一下", "查查", "查看", "看看", "了解", "询问");
        boolean hasStatusIntent = containsAny(lower,
                "status", "progress", "when", "how long", "arrival",
                "进度", "状态", "到账", "到哪", "什么时候", "多久", "何时",
                "完成了吗", "处理了吗", "办好了吗", "到了吗");
        boolean hasAfterSalesTopic = containsAny(lower,
                "return", "refund", "exchange", "cancel", "delivery", "logistics",
                "退", "退款", "退货", "换货", "取消", "售后", "物流");
        if (hasStatusIntent && hasAfterSalesTopic) {
            return true;
        }
        if (hasQueryIntent && hasStatusIntent) {
            return true;
        }
        return hasQueryIntent && hasAfterSalesTopic;
    }

    private static boolean hasActionIntentMarkers(String text) {
        String lower = text.toLowerCase();
        return ACTION_INTENT_MARKERS.stream().anyMatch(m -> lower.contains(m.toLowerCase()))
                || DANGEROUS_QUERY_MARKERS.stream().anyMatch(m -> lower.contains(m.toLowerCase()));
    }

    /** A standalone short phrase expressing a sensitive intent, e.g. "refund" or "cancel". */
    private static boolean isStandaloneSensitiveIntent(String text) {
        if (isQueryOrStatusIntent(text)) {
            return false;
        }
        String normalized = text.toLowerCase().replaceAll("[？?。！!，,、\\s]", "");
        if (STANDALONE_SENSITIVE_TERMS.stream()
                .anyMatch(t -> t.equalsIgnoreCase(normalized))) {
            return true;
        }
        if (normalized.length() <= 16) {
            return STANDALONE_SENSITIVE_TERMS.stream()
                    .anyMatch(t -> normalized.contains(t.toLowerCase()));
        }
        return false;
    }

    private static boolean containsAny(String text, String... parts) {
        for (String part : parts) {
            if (text.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
