package com.github.adamyork.elaborate.service;

import com.github.adamyork.elaborate.model.CallObject;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class PrinterService {

    private final String className;
    private final String methodName;

    public PrinterService(final String className, final String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    public void print(final List<CallObject> callObjects, final int indentationLevel) {
        String tabs = StringUtils.repeat("\t", indentationLevel);
        System.out.println(tabs + className + "::" + methodName + " calls");
        final int nextIndentationLevel = indentationLevel + 1;
        tabs = StringUtils.repeat("\t", indentationLevel + 1);
        for (final CallObject callObject : callObjects) {
            if (callObject.getCallObjects().size() > 0) {
                final PrinterService printerService = new PrinterService(callObject.getType(), callObject.getMethod());
                printerService.print(callObject.getCallObjects(), nextIndentationLevel);
            } else {
                System.out.println(tabs + callObject.getType() + "::" + callObject.getMethod());
            }
        }
    }
}
