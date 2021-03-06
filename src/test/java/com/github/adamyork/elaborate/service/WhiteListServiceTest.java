package com.github.adamyork.elaborate.service;

import com.github.adamyork.elaborate.model.MethodInvocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("WeakerAccess")
public class WhiteListServiceTest {

    @Test
    public void testWhiteListFiltering() {

        final List<MethodInvocation> innerAAInvocations = new ArrayList<>();
        final MethodInvocation innerAAA = new MethodInvocation.Builder("com.pkg.InnerAAA", "innerAAAMethod", "").build();
        innerAAInvocations.add(innerAAA);

        final List<MethodInvocation> innerBInvocations = new ArrayList<>();
        final MethodInvocation innerBA = new MethodInvocation.Builder("com.pkg.InnerBA", "innerBAMethod", "", innerAAInvocations).build();
        innerBInvocations.add(innerBA);

        final List<MethodInvocation> innerAInvocations = new ArrayList<>();
        final MethodInvocation innerAA = new MethodInvocation.Builder("com.pkg.InnerAA", "innerAAMethod", "", innerAAInvocations).build();
        final MethodInvocation innerAB = new MethodInvocation.Builder("com.pkg.InnerAB", "innerABMethod", "", new ArrayList<>()).build();
        innerAInvocations.add(innerAA);
        innerAInvocations.add(innerAB);

        final List<MethodInvocation> rootMethodInvocations = new ArrayList<>();
        final MethodInvocation innerA = new MethodInvocation.Builder("com.pkg.InnerA", "innerAMethod", "", innerAInvocations).build();
        final MethodInvocation innerB = new MethodInvocation.Builder("com.pkg.InnerB", "innerBMethod", "", innerBInvocations).build();
        final MethodInvocation innerC = new MethodInvocation.Builder("com.pkg.InnerC", "innerCMethod", "", new ArrayList<>()).build();
        rootMethodInvocations.add(innerA);
        rootMethodInvocations.add(innerB);
        rootMethodInvocations.add(innerC);

        final MethodInvocation rootInvocation = new MethodInvocation.Builder("com.pkg.RootClass", "startMethod",
                "", rootMethodInvocations).build();

        final List<String> whitelist = new ArrayList<>();
        whitelist.add("com.pkg.InnerAAA::innerAAAMethod");
        final WhiteListService whiteListService = new WhiteListService();
        final Optional<MethodInvocation> maybeFiltered = whiteListService.filter(rootInvocation, whitelist);
        final MethodInvocation result = maybeFiltered.orElseGet(() -> new MethodInvocation.Builder("com.pkg.EmptyClass", "",
                "", new ArrayList<>()).build());

        assertEquals(1, result.getMethodInvocations().get(0).getMethodInvocations().size());
        assertEquals(result.getMethodInvocations().get(1).getMethodInvocations().size(), 1);
        assertEquals(result.getMethodInvocations().get(0).getMethodInvocations().get(0).getMethodInvocations().size(), 1);
        assertEquals(result.getMethodInvocations().get(1).getMethodInvocations().get(0).getMethodInvocations().size(), 1);
        assertEquals(result.getMethodInvocations().get(0).getMethodInvocations().get(0).getMethodInvocations().get(0).getMethodInvocations().size(), 0);
        assertEquals(result.getMethodInvocations().get(1).getMethodInvocations().get(0).getMethodInvocations().get(0).getMethodInvocations().size(), 0);
        assertEquals("com.pkg.InnerAAA", result.getMethodInvocations().get(0).getMethodInvocations().get(0).getMethodInvocations().get(0).getType());
        assertEquals("com.pkg.InnerAAA", result.getMethodInvocations().get(1).getMethodInvocations().get(0).getMethodInvocations().get(0).getType());
    }

}
