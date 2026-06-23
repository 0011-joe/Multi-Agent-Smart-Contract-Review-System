package com.contract.review.agent;

import java.util.List;
import java.util.Map;

/**
 * Agent 基础抽象类
 * 定义所有智能体的核心接口：
 * - execute() - Agent 核心执行方法
 * - getAgentRole() - Agent 角色标识
 * - getCapabilities() - Agent 能力列表
 */
public abstract class AgentBase {

    /** Agent 名称 */
    protected final String agentName;

    /** Agent 角色描述 */
    protected final String agentRole;

    /** Agent 版本 */
    protected final String version;

    protected AgentBase(String agentName, String agentRole, String version) {
        this.agentName = agentName;
        this.agentRole = agentRole;
        this.version = version;
    }

    /**
     * Agent 核心执行方法
     * @param inputs 输入参数列表（如合同文本、条款列表等）
     * @return Agent 执行结果（结构化 Map）
     */
    public abstract Map<String, Object> execute(List<String> inputs);

    /**
     * 获取 Agent 角色标识
     * @return 角色描述字符串
     */
    public String getAgentRole() {
        return agentRole;
    }

    /**
     * 获取 Agent 能力列表
     * @return 能力名称列表
     */
    public abstract List<String> getCapabilities();

    /**
     * 获取 Agent 名称
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * 获取 Agent 版本
     */
    public String getVersion() {
        return version;
    }

    /**
     * 初始化 Agent（子类可重写）
     */
    public void init() {
        // 默认空实现
    }

    /**
     * 销毁 Agent（子类可重写）
     */
    public void destroy() {
        // 默认空实现
    }

    @Override
    public String toString() {
        return "Agent{name='" + agentName + "', role='" + agentRole + "', version='" + version + "'}";
    }
}
