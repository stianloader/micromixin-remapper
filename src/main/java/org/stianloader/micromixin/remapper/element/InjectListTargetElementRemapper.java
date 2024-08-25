package org.stianloader.micromixin.remapper.element;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.micromixin.remapper.MicromixinRemapper;
import org.stianloader.micromixin.remapper.MissingFeatureException;
import org.stianloader.micromixin.remapper.RemapContext;

public class InjectListTargetElementRemapper implements AnnotationElementRemapper<List<?>> {

    public static final InjectListTargetElementRemapper INSTANCE = new InjectListTargetElementRemapper();

    @Override
    @NotNull
    public List<?> remapNode(@NotNull RemapContext ctx, @NotNull List<?> originalValue) throws MissingFeatureException, IllegalMixinException {
        Type[] arguments = Type.getArgumentTypes(ctx.mixinMethod.desc);
        boolean expectVoid = false;
        boolean foundCallbackInfo = false;
        int callbackInfoArgument;
        for (callbackInfoArgument = 0; callbackInfoArgument < arguments.length; callbackInfoArgument++) {
            Type arg = arguments[callbackInfoArgument];
            String descriptor = arg.getDescriptor();
            if ((expectVoid = descriptor.equals("L" + MicromixinRemapper.CALLBACK_INFO_CLASS + ";")) || descriptor.equals("L" + MicromixinRemapper.CALLBACK_INFO_RETURNABLE_CLASS + ";")) {
                foundCallbackInfo = true;
                break;
            }
        }

        if (!foundCallbackInfo) {
            throw new IllegalMixinException("Annotated method " + ctx.mixinClassName + "." + ctx.mixinMethod.name + ctx.mixinMethod.desc + " lacks type argument " + MicromixinRemapper.CALLBACK_INFO_CLASS + " or " + MicromixinRemapper.CALLBACK_INFO_RETURNABLE_CLASS);
        }

        final boolean expectVoidFinal = expectVoid; // Lambda workarounds
        final int capturedArgumentCount = callbackInfoArgument;

        ctx.remapper.remapMethodSelectorList(originalValue, ctx.mixinClassName, ctx.mixinMethod, ctx.targets, (inferredDescriptor) -> {
            if (inferredDescriptor.codePointAt(0) != '('
                    || expectVoidFinal != (inferredDescriptor.codePointBefore(inferredDescriptor.length()) == 'V')) {
                return false;
            }

            Type[] inferredDescriptorArguments = Type.getArgumentTypes(inferredDescriptor);
            if (inferredDescriptorArguments.length < capturedArgumentCount) {
                return false;
            }

            for (int j = 0; j < capturedArgumentCount; j++) {
                if (!arguments[j].getDescriptor().equals(inferredDescriptorArguments[j].getDescriptor())) {
                    return false;
                }
            }
            return true;
        });
        return originalValue;
    }
}
