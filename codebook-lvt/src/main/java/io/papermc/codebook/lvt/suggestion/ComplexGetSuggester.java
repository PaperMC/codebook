package io.papermc.codebook.lvt.suggestion;

import static io.papermc.codebook.lvt.LvtUtil.staticFinalFieldNameToLocalName;

import io.papermc.codebook.lvt.LvtUtil;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class ComplexGetSuggester implements LvtSuggester {

    private static final StaticFieldEntry BLOCK_STATE_PROPERTY = new StaticFieldEntry(
            Set.of(
                    "net/minecraft/world/level/block/state/StateHolder",
                    "net/minecraft/world/level/block/state/BlockState",
                    "net/minecraft/world/level/block/state/BlockBehaviour$Properties",
                    "net/minecraft/world/level/material/FluidState"
            ),
            Set.of(
                    Map.entry("getValue", "(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;")
            ),
            Set.of(
                    "Lnet/minecraft/world/level/block/state/properties/IntegerProperty;",
                    "Lnet/minecraft/world/level/block/state/properties/BooleanProperty;"
            ),
            "Value"
    );

    @Override
    public @Nullable String suggestFromMethod(final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container) throws IOException {
        final MethodInsnNode node = insn.node();
        if (BLOCK_STATE_PROPERTY.test(node)) {
            return BLOCK_STATE_PROPERTY.transform(node);
        }
        return null;
    }

    private record StaticFieldEntry(Set<String> owners, Set<Entry<String, String>> methods, Set<String> fieldTypes, @Nullable String suffix) {

        boolean test(final MethodInsnNode node) {
            return this.owners.contains(node.owner) && this.methods.stream().anyMatch(e -> e.getKey().equals(node.name) && e.getValue().equals(node.desc));
        }

        @Nullable String transform(final MethodInsnNode node) {
            final AbstractInsnNode prev = node.getPrevious();
            if (prev instanceof final FieldInsnNode fieldInsnNode && fieldInsnNode.getOpcode() == Opcodes.GETSTATIC && this.fieldTypes.contains(fieldInsnNode.desc)) {
                return staticFinalFieldNameToLocalName(fieldInsnNode.name) + (this.suffix == null ? "" : this.suffix);
            }
            return null;
        }
    }

}
