package io.papermc.codebook.lvt.suggestion.context;

import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.VarInsnNode;

public record AssignmentContext(VarInsnNode assignmentNode, LocalVariableNode lvt) {
}
