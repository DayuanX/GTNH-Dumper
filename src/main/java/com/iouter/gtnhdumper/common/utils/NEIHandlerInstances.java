package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;

import codechicken.nei.recipe.TemplateRecipeHandler;

/** Query instances retain the registration's configuration without changing its cached recipes. */
public final class NEIHandlerInstances {

    private NEIHandlerInstances() {}

    public static TemplateRecipeHandler create(TemplateRecipeHandler registered) {
        if (!registered.getClass()
            .getName()
            .equals("galaxyspace.core.nei.CustomRockRecipeHandler")) {
            return registered.newInstance();
        }
        return (TemplateRecipeHandler) copyRocketRegistration(registered);
    }

    // GalaxySpace's inherited factory constructs the base class, losing GTNL's Steam Rocket IDs.
    static Object copyRocketRegistration(Object registered) {
        try {
            Class<?> type = registered.getClass();
            Class<?> base = type.getSuperclass();
            Object copy = type.getConstructor(int.class, String.class, int.class, int.class, String.class, String.class)
                .newInstance(
                    value(base, registered, "tier"),
                    value(base, registered, "textureSuffix"),
                    value(base, registered, "y"),
                    value(base, registered, "h"),
                    type.getField("recipeId")
                        .get(registered),
                    type.getField("handlerId")
                        .get(registered));
            Field recipes = base.getDeclaredField("recipes");
            recipes.setAccessible(true);
            recipes.set(copy, recipes.get(registered));
            return copy;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot preserve custom rocket handler registration", error);
        }
    }

    private static Object value(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }
}
