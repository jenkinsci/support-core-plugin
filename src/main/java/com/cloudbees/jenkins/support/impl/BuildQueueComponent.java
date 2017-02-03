/*
 * The MIT License
 * 
 * Copyright (c) 2015 schristou88
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.model.BuildQueue;
import com.cloudbees.jenkins.support.util.Helper;
import com.cloudbees.jenkins.support.util.SupportUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Gather information about the current build queue. This will
 * also gather information about the cause of blockage.
 *
 * @author schristou88
 */
@Extension
public class BuildQueueComponent extends Component {
  @NonNull
  @Override
  public Set<Permission> getRequiredPermissions() {
    // This could contain sensitive information about a build the
    // user might not have permission to.
    return Collections.singleton(Jenkins.ADMINISTER);
  }

  @NonNull
  @Override
  public String getDisplayName() {
    return "Build queue";
  }

  @Override
  public void addContents(@NonNull Container container) {
    container.add(new Content("buildqueue.yaml") {
        @Override
        public void writeTo(OutputStream os) throws IOException {
          PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, "utf-8")));
          try {
            List<Queue.Item> items = Helper.getActiveInstance().getQueue().getApproximateItemsQuickly();
            BuildQueue bq = new BuildQueue();
            bq.setSize(items.size());

            for (Queue.Item item : items) {
              BuildQueue.Item bqItem = new BuildQueue.Item();
              if (item instanceof Item) {
                bqItem.setFullName( ((Item) item).getFullName());
              }
              else {
                bqItem.setFullName(Functions.escape(item.task.getFullDisplayName()));
              }

              bqItem.setQueueTime(item.getInQueueForString());
              bqItem.setBlocked(item.isBlocked());
              bqItem.setWhyInQueue(item.getWhy());

              for (Cause cause : item.getCauses()) {
                BuildQueue.Item.Cause c = new BuildQueue.Item.Cause();
                c.setDescription(cause.getShortDescription());
                bqItem.addCause(c);
              }

              for (QueueTaskDispatcher taskDispatcher : QueueTaskDispatcher.all()) {
                BuildQueue.Item.TaskDispatcher td = new BuildQueue.Item.TaskDispatcher();
                td.setName(taskDispatcher.toString());
                CauseOfBlockage causeOfBlockage = taskDispatcher.canRun(item);
                td.setCanRun((causeOfBlockage == null) ? "null" : causeOfBlockage.toString());

                bqItem.addTaskDispatcher(td);
              }

              bq.addItem(bqItem);
            }

            out.println(SupportUtils.toString(bq));
          } finally {
            out.flush();
          }
        }
      }
    );
  }
}
