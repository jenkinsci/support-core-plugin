package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import jenkins.AgentProtocol;
import jenkins.model.Jenkins;

@Extension
public class AgentProtocols extends Component {

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Agent Protocols";
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new PrintedContent("agent-protocols.md") {

            @Override
            protected void printTo(PrintWriter out) throws IOException {
                try {
                    Set<String> agentProtocols = Jenkins.get().getAgentProtocols();
                    out.println("Active protocols");
                    out.println("================");
                    out.println();
                    AgentProtocol.all().stream()
                            .filter(agentProtocol ->
                                    agentProtocol.getName() != null && agentProtocols.contains(agentProtocol.getName()))
                            .forEach(agentProtocol -> {
                                out.println(" * `" + agentProtocol.getName() + "`: " + agentProtocol.getDisplayName());
                                out.println("    * Deprecated: " + agentProtocol.isDeprecated());
                                out.println("    * Required: " + agentProtocol.isRequired());
                                out.println("    * Opt In: " + agentProtocol.isOptIn());
                            });
                    out.println();
                    out.println("Inactive protocols");
                    out.println("==================");
                    out.println();
                    AgentProtocol.all().stream()
                            .filter(agentProtocol -> agentProtocol.getName() != null
                                    && !agentProtocols.contains(agentProtocol.getName()))
                            .forEach(agentProtocol -> {
                                out.println(" * `" + agentProtocol.getName() + "`: " + agentProtocol.getDisplayName());
                                out.println("    * Deprecated: " + agentProtocol.isDeprecated());
                                out.println("    * Required: " + agentProtocol.isRequired());
                                out.println("    * Opt In: " + agentProtocol.isOptIn());
                            });
                } finally {
                    out.flush();
                }
            }

            @Override
            public boolean shouldBeFiltered() {
                return false;
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.AGENT;
    }
}
