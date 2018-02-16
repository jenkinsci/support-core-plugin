package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.ContentData;
import com.cloudbees.jenkins.support.api.TruncatedContent;
import com.cloudbees.jenkins.support.api.TruncationException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dump export table of nodes to detect potential
 * memory leaks.
 *
 * @since 2.23
 *
 * User: schristou88
 * Date: 1/8/15
 * Time: 3:54 PM
 */
@Extension
public class DumpExportTable extends Component {
  private final Logger logger = Logger.getLogger(DumpExportTable.class.getName());

  @NonNull
  @Override
  public Set<Permission> getRequiredPermissions() {
    return Collections.singleton(Jenkins.ADMINISTER);
  }

  @NonNull
  @Override
  public String getDisplayName() {
    return "Dump agent export tables (could reveal some memory leaks)";
  }

  @Override
  public void addContents(@NonNull Container container) {
    addContents(container, false);
  }

  @Override
  public void addContents(@NonNull Container result, boolean shouldAnonymize) {
    for (final Node node : Jenkins.getInstance().getNodes()) {
      result.add(
        new TruncatedContent(new ContentData("nodes/slave/" + getNodeName(node, shouldAnonymize) + "/exportTable.txt", shouldAnonymize)) {
          @Override
          protected void printTo(PrintWriter out) throws IOException {
            try {

              VirtualChannel channel = node.getChannel();
              if (channel instanceof Channel) // Should never be false but just in case.
              {
                ((Channel)channel).dumpExportTable(out);
              }
            } catch (TruncationException e) {
              logger.log(Level.WARNING,
                      "Truncated the output of export table for node: " + getNodeName(node, shouldAnonymize), e);
            } catch (IOException e) {
              logger.log(Level.WARNING,
                      "Could not record environment of node " + getNodeName(node, shouldAnonymize), e);
            }
          }
        }
      );
    }
  }
  
  private static final long serialVersionUID = 1L;
}