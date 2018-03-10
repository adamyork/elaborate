package com.github.adamyork.elaborate.model;

import java.util.List;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class CallObject {

    private String type;
    private String method;
    private List<CallObject> callObjects;

    public CallObject(final String type,
                      final String method) {
        this.type = type;
        this.method = method;
    }

    public String getType() {
        return type;
    }

    public String getMethod() {
        return method;
    }

    public List<CallObject> getCallObjects() {
        return callObjects;
    }

    public void setCallObjects(final List<CallObject> callObjects) {
        this.callObjects = callObjects;
    }

}
