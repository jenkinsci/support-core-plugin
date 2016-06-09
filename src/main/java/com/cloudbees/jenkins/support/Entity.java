package com.cloudbees.jenkins.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by minudika on 6/9/16.
 */
public class Entity {
    private boolean isDirectory = false;
    private String name = "";
    private List<Entity> children = new ArrayList<Entity>();


    public boolean isDirectory() {
        return isDirectory;
    }

    public void setName(String name){
        this.name = name;
    }
    public String getName() {
        return name;
    }

    public List<Entity> getChildren() {
        return children;
    }

    public void addChild(Entity e){
        this.children.add(e);
    }
}
