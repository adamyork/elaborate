package com.github.adamyork.elaborate.service;

import com.github.adamyork.elaborate.model.MethodInvocation;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class WhiteListService {

    public static List<MethodInvocation> manageList(final List<MethodInvocation> methodInvocations,
                                                    final Optional<List<String>> maybeWhiteList,
                                                    final String className,
                                                    final String methodName) {
        if (maybeWhiteList.isPresent()) {
            final MethodInvocation root = new MethodInvocation.Builder(className, methodName, "", methodInvocations).build();
            final Optional<MethodInvocation> probablyFiltered = filter(root, maybeWhiteList.get());
            if (probablyFiltered.isPresent()) {
                return probablyFiltered.get().getMethodInvocations();
            }
            return methodInvocations;
        }
        return methodInvocations;
    }

    public static Optional<MethodInvocation> filter(final MethodInvocation node, final List<String> matchers) {
        final boolean match = matchers.stream().anyMatch(matcher -> matcher.equals(node.getType() + "::" + node.getMethod()));
        if (match) {
            return Optional.of(node);
        }
        final List<MethodInvocation> filtered = node.getMethodInvocations().stream()
                .map(child -> WhiteListService.filter(child, matchers))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        if (filtered.size() == 0) {
            return Optional.empty();
        }
        return Optional.of(new MethodInvocation.Builder(node.getType(), node.getMethod(), node.getArguments(), filtered).build());
    }
}
