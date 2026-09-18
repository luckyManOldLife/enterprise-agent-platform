package com.xr.agent.policy;

import com.xr.agent.application.port.out.PolicyEnginePort;
import com.xr.agent.domain.model.RiskLevel;
import com.xr.agent.domain.model.ToolDefinition;

import java.util.Objects;
import java.util.Set;

/**
 * Deterministic policy gate used before any tool execution.
 *
 * <p>Model output is never treated as an authorization decision. The caller must
 * provide the actor roles and an idempotency key for write-capable tools.</p>
 */
public final class RuleBasedPolicyEngine implements PolicyEnginePort {

    private final Set<String> writeRoles;
    private final Set<String> approvalRoles;

    public RuleBasedPolicyEngine(Set<String> writeRoles, Set<String> approvalRoles) {
        this.writeRoles = Set.copyOf(Objects.requireNonNull(writeRoles, "writeRoles"));
        this.approvalRoles = Set.copyOf(Objects.requireNonNull(approvalRoles, "approvalRoles"));
    }

    public static RuleBasedPolicyEngine defaults() {
        return new RuleBasedPolicyEngine(
                Set.of("agent.write", "operator"),
                Set.of("operator", "approver"));
    }

    @Override
    public PolicyDecision authorize(PolicyRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.task(), "task");
        ToolDefinition tool = Objects.requireNonNull(request.tool(), "tool");

        if (tool.capabilities().isEmpty()) {
            return deny("POLICY_TOOL_CAPABILITIES_MISSING", "Tool has no declared capabilities");
        }

        if (isWriteTool(tool) && request.roles().stream().noneMatch(writeRoles::contains)) {
            return deny("POLICY_WRITE_ROLE_REQUIRED", "Actor is not allowed to execute write tools");
        }

        if (isWriteTool(tool) && isBlank(request.idempotencyKey())) {
            return deny("POLICY_IDEMPOTENCY_KEY_REQUIRED", "Write tools require an idempotency key");
        }

        if (tool.riskLevel() == RiskLevel.CRITICAL) {
            if (request.roles().stream().noneMatch(approvalRoles::contains)) {
                return new PolicyDecision(
                        true,
                        true,
                        "Critical operation requires an independent human approval",
                        "POLICY_CRITICAL_APPROVAL_REQUIRED");
            }
            return new PolicyDecision(
                    true,
                    true,
                    "Critical operation still requires recorded human approval",
                    "POLICY_CRITICAL_APPROVAL_REQUIRED");
        }

        if (tool.requiresApproval()) {
            return new PolicyDecision(
                    true,
                    true,
                    "High-risk operation requires recorded human approval",
                    "POLICY_HIGH_RISK_APPROVAL_REQUIRED");
        }

        return new PolicyDecision(true, false, "Allowed by deterministic policy", "POLICY_ALLOWED");
    }

    private boolean isWriteTool(ToolDefinition tool) {
        return tool.capabilities().stream()
                .map(String::toLowerCase)
                .anyMatch(capability -> capability.endsWith(".write")
                        || capability.contains(":write")
                        || capability.equals("write"));
    }

    private static PolicyDecision deny(String code, String reason) {
        return new PolicyDecision(false, false, reason, code);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
