package org.stianloader.micromixin.remapper.element;

import java.util.ArrayList;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.stianloader.micromixin.remapper.RemapContext;

public class ImplicitTargetElementResolver implements BiConsumer<@NotNull RemapContext, @NotNull AnnotationNode> {

    @NotNull
    public static final ImplicitTargetElementResolver INSTANCE = new ImplicitTargetElementResolver();

    @Override
    public void accept(@NotNull RemapContext context, @NotNull AnnotationNode annotation) {
        if (annotation.values == null) {
            annotation.values = new ArrayList<>();
        }

        int i = annotation.values.size();
        while (i-- != 0) {
            String key = (String) annotation.values.get(--i);
            if (key.equals("method") || key.equals("target")) {
                break;
            }
        }

        if (i < 0) {
            annotation.values.add("target");
            AnnotationNode descAnnotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Desc;");
            descAnnotation.values = new ArrayList<>();
            descAnnotation.values.add("value");
            descAnnotation.values.add(context.mixinMethod.name);
            descAnnotation.values.add("args");
            descAnnotation.values.add(Type.getArgumentTypes(context.mixinMethod.desc));
            descAnnotation.values.add("ret");
            descAnnotation.values.add(Type.getReturnType(context.mixinMethod.desc));
            annotation.values.add(descAnnotation);
        }
    }
}
