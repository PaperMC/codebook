package io.papermc.codebook.report.type;

import dev.denwav.hypo.hydrate.generic.LambdaClosure;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.MethodData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import org.cadixdev.lorenz.model.Mapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.checkerframework.checker.nullness.qual.Nullable;

public class MissingMethodParam implements Report {

    private final Map<ClassData, List<String>> data = new ConcurrentHashMap<>();

    public void reportMissingParam(final MethodData method, final @Nullable MethodMapping methodMapping, final int descriptorParamOffset, final IntUnaryOperator descriptorToMappingOffset, final @Nullable LambdaClosure lambdaClosure) {
        final ClassData parentClass = method.parentClass();
        final StringBuilder msg = new StringBuilder("\t#%s %s".formatted( method.name(), method.descriptorText()));
        if (lambdaClosure != null) {
            final MethodData containingMethod = lambdaClosure.getContainingMethod();
            msg.append("%n\t\tLambda Source: %s#%s %s".formatted(containingMethod.parentClass().equals(parentClass) ? "" : containingMethod.parentClass().name(), containingMethod.name(), containingMethod.descriptorText()));
        }
        for (int i = descriptorParamOffset; i < method.params().size(); i++) {
            final int paramIdx = i;
            final int lastIdxOfDot = method.param(i ).toString().lastIndexOf('.');
            msg.append("%n\t\t%s\t%-50s\t%s".formatted(i, method.param(i).toString().substring(lastIdxOfDot + 1), Optional.ofNullable(methodMapping).flatMap(m -> m.getParameterMapping(descriptorToMappingOffset.applyAsInt(paramIdx))).map(Mapping::getDeobfuscatedName).orElse("<<MISSING>>")));
        }
        this.data.computeIfAbsent(parentClass, ignored -> new ArrayList<>()).add(msg.toString());
    }

    @Override
    public String generate() {
        final StringBuilder output = new StringBuilder();
        this.data.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .forEach(entry -> {
                    output.append("Missing param mappings in %s, Method Count: %s, Param Count: TODO\n".formatted(entry.getKey().name(), entry.getValue().size()));
                    entry.getValue().forEach(msg -> output.append(msg).append("\n"));
                });
        return output.toString();
    }
}
