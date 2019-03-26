package com.github.adamyork.elaborate.service;

import com.github.adamyork.elaborate.model.MethodInvocation;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class ConsoleService extends AbstractPrintService {

    public int print(final String className, final String methodName, final List<MethodInvocation> methodInvocations,
                     final int indentationLevel) {
        final String initialTabs = StringUtils.repeat("\t", indentationLevel);
        final String immediateOutput = normalizeObjectCreation(className, methodName, " calls");
        System.out.println(initialTabs + immediateOutput);
        final int nextIndentationLevel = indentationLevel + 1;
        final String subsequentTabs = StringUtils.repeat("\t", indentationLevel + 1);
        return methodInvocations.stream().map(methodInvocation -> {
            if (methodInvocation.getMethodInvocations().size() > 0) {
                return print(methodInvocation.getType(), methodInvocation.getMethod(), methodInvocation.getMethodInvocations(),
                        nextIndentationLevel);
            } else {
                final String innerOutput = normalizeObjectCreation(methodInvocation.getType(),
                        methodInvocation.getMethod(), "");
                System.out.println(subsequentTabs + innerOutput);
                return 0;
            }
        }).mapToInt(value -> value).sum();
    }

}
