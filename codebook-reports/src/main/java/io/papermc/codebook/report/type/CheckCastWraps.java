package io.papermc.codebook.report.type;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.tree.MethodInsnNode;

public class CheckCastWraps implements Report {

    private final Map<CacheKey, Integer> cache = new ConcurrentHashMap<>();

    @Override
    public String generate() {
        final StringBuilder sb = new StringBuilder();
        this.cache.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEachOrdered(entry -> {
            sb.append(entry.getKey().className).append("#").append(entry.getKey().methodName).append(" ")
                    .append(entry.getKey().descriptor).append(" ").append(entry.getKey().itf).append(" ")
                    .append(entry.getValue()).append("\n");
        });
        return sb.toString();
    }

    public void report(final MethodInsnNode insn) {
        final var key = new CacheKey(insn.owner, insn.name, insn.desc, insn.itf);
        this.cache.compute(key, (k, v) -> v == null ? 1 : v + 1);
    }

    private record CacheKey(String className, String methodName, String descriptor, boolean itf) {
    }
}
