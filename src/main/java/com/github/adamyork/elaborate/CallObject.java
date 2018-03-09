package com.github.adamyork.elaborate;

public class CallObject {

    private String type;
    private String method;

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

}
