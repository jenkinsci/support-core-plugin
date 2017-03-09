package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BuildQueue implements Serializable, MarkdownFile {
    List<Item> buildQueue = new ArrayList<>();
    int size;
    boolean isQuietingDown;

    public void addItem(Item item) {
        buildQueue.add(item);
    }

    public List<Item> getBuildQueue() {
        return buildQueue;
    }

    public void setBuildQueue(List<Item> buildQueue) {
        this.buildQueue = buildQueue;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isQuietingDown() {
        return isQuietingDown;
    }

    public void setQuietingDown(boolean quietingDown) {
        isQuietingDown = quietingDown;
    }

    public static class Item implements Serializable {
        String fullName;
        boolean isBlocked;
        String queueTime;
        String whyInQueue;
        List<Cause> causeList = new ArrayList<>();
        List<TaskDispatcher> taskDispatcherList = new ArrayList<>();

        public void addCause(Cause cause) {
            causeList.add(cause);
        }

        public void addTaskDispatcher(TaskDispatcher td) {
            taskDispatcherList.add(td);
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public boolean isBlocked() {
            return isBlocked;
        }

        public void setBlocked(boolean blocked) {
            isBlocked = blocked;
        }

        public String getQueueTime() {
            return queueTime;
        }

        public void setQueueTime(String queueTime) {
            this.queueTime = queueTime;
        }

        public String getWhyInQueue() {
            return whyInQueue;
        }

        public void setWhyInQueue(String whyInQueue) {
            this.whyInQueue = whyInQueue;
        }

        public List<Cause> getCauseList() {
            return causeList;
        }

        public void setCauseList(List<Cause> causeList) {
            this.causeList = causeList;
        }

        public List<TaskDispatcher> getTaskDispatcherList() {
            return taskDispatcherList;
        }

        public void setTaskDispatcherList(List<TaskDispatcher> taskDispatcherList) {
            this.taskDispatcherList = taskDispatcherList;
        }

        public static class Cause implements Serializable {
            String description;

            public String getDescription() {
                return description;
            }

            public void setDescription(String description) {
                this.description = description;
            }
        }


        public static class TaskDispatcher implements Serializable {
            String name;
            String canRun;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getCanRun() {
                return canRun;
            }

            public void setCanRun(String canRun) {
                this.canRun = canRun;
            }
        }
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("Current build queue has " +  size + " item(s).");
        out.println("---------------");

        for (Item item : buildQueue) {
                out.println(" * Name of item: " + item.getFullName());

            out.println("    - In queue for: " + item.getQueueTime());
            out.println("    - Is blocked: " + item.isBlocked());
            out.println("    - Why in queue: " + item.getWhyInQueue());

            for (Item.Cause cause : item.getCauseList()) {
                out.println("    - Current queue trigger cause: " + cause.getDescription());
            }

            for (Item.TaskDispatcher taskDispatcher : item.getTaskDispatcherList()) {
                out.println("  * Task Dispatcher: " + taskDispatcher);
                out.println("    - Can run: " + taskDispatcher.getCanRun());
            }
            out.println("----");
            out.println();
        }

        out.println("Is quieting down: " + isQuietingDown());
    }
}
