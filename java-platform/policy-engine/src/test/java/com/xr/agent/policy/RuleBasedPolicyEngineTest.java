package com.xr.agent.policy;

import com.xr.agent.application.port.out.PolicyEnginePort;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.RiskLevel;
import com.xr.agent.domain.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedPolicyEngineTest {

    private final RuleBasedPolicyEngine policy = RuleBasedPolicyEngine.defaults();

    @Test
    void deniesWriteWithoutRoleOrIdempotencyKey() {
        PolicyEnginePort.PolicyDecision decision = policy.authorize(new PolicyEnginePort.PolicyRequest(
                task(),
                tool(RiskLevel.LOW, "support.write"),
                Set.of("agent.read"),
                null));

        assertFalse(decision.allowed());
        assertFalse(decision.requiresApproval());
    }

    @Test
    void requiresApprovalForHighRiskWrite() {
        PolicyEnginePort.PolicyDecision decision = policy.authorize(new PolicyEnginePort.PolicyRequest(
                task(),
                tool(RiskLevel.HIGH, "support.write"),
                Set.of("agent.write"),
                "idem-001"));

        assertTrue(decision.allowed());
        assertTrue(decision.requiresApproval());
    }

    @Test
    void allowsReadWithoutApproval() {
        PolicyEnginePort.PolicyDecision decision = policy.authorize(new PolicyEnginePort.PolicyRequest(
                task(),
                tool(RiskLevel.LOW, "order.read"),
                Set.of("agent.read"),
                null));

        assertTrue(decision.allowed());
        assertFalse(decision.requiresApproval());
    }

    private static AgentTask task() {
        return AgentTask.create(
                "tenant-a",
                "user-a",
                "trace-a",
                "conversation-a",
                "supervisor",
                "support-agent",
                Map.of(),
                null);
    }

    private static ToolDefinition tool(RiskLevel risk, String capability) {
        return new ToolDefinition(
                "support.tool",
                "Support tool",
                "1.0.0",
                risk,
                Set.of(capability),
                true);
    }
}
