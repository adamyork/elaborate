package com.github.adamyork.elaborate.service;

import com.github.adamyork.elaborate.model.MethodInvocation;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class WhiteListService {

    public List<MethodInvocation> manageList(final List<MethodInvocation> methodInvocations,
                                             final Optional<List<String>> maybeWhiteList,
                                             final String className,
                                             final String methodName) {
        return maybeWhiteList.map(whiteList -> probablyFilterList(methodInvocations, whiteList, className, methodName))
                .or(() -> Optional.of(methodInvocations))
                .get();
    }

    public Optional<MethodInvocation> filter(final MethodInvocation node, final List<String> matchers) {
        final String classAndMethod = node.getType() + "::" + node.getMethod();
        final boolean match = matchers.stream().anyMatch(matcher -> matcher.equals(classAndMethod));
        if (match) {
            final List<MethodInvocation> pruned = node.getMethodInvocations().stream()
                    .filter(invocation -> invocation.getType().equals(classAndMethod))
                    .collect(Collectors.toList());
            final MethodInvocation postFilteredNode = new MethodInvocation.Builder(node.getType(), node.getMethod(),
                    node.getArguments(), true, node.discreet(), pruned).build();
            return Optional.of(postFilteredNode);
        }
        final List<MethodInvocation> filtered = node.getMethodInvocations().stream()
                .map(child -> filter(child, matchers))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        if (filtered.size() == 0) {
            return Optional.empty();
        }
        return Optional.of(new MethodInvocation.Builder(node.getType(), node.getMethod(), node.getArguments(), filtered).build());
    }

    private List<MethodInvocation> probablyFilterList(final List<MethodInvocation> methodInvocations,
                                                      final List<String> maybeWhiteList,
                                                      final String className,
                                                      final String methodName) {
        final MethodInvocation root = new MethodInvocation.Builder(className, methodName, "",
                methodInvocations).build();
        final Optional<MethodInvocation> probablyFiltered = filter(root, maybeWhiteList);
        return probablyFiltered.map(MethodInvocation::getMethodInvocations)
                .or(() -> Optional.of(methodInvocations))
                .get();
    }
}
