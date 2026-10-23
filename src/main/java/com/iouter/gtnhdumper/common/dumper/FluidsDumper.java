package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.iouter.gtnhdumper.CommonProxy;
import com.iouter.gtnhdumper.common.base.WikiDumper;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.Utils;

import gregtech.api.enums.ItemList;

public class FluidsDumper extends WikiDumper {

    private static final String KEY = "key";
    private static final String ORIGINAL_NAME = "originalName";
    private static final String TRANSLATED_NAME = "translatedName";
    private static final String LUMINOSITY = "luminosity";
    private static final String DENSITY = "density";
    private static final String TEMPERATURE = "temperature";
    private static final String VISCOSITY = "viscosity";
    private static final String IS_GASEOUS = "isGaseous";
    private static final String CAN_BE_PLACED = "canBePlaced";
    private static final String REGISTERED_BY = "registeredBy";

    private static final String GT_DISPLAY_KEY = "gtDisplayKey";
    private static final String TOOLTIPS = "tooltips";

    private static final String[] HEADER = new String[] { KEY, ORIGINAL_NAME, TRANSLATED_NAME, LUMINOSITY, DENSITY,
        TEMPERATURE, VISCOSITY, IS_GASEOUS, CAN_BE_PLACED, REGISTERED_BY };

    public FluidsDumper() {
        super("tools.dump.gtnhdumper.fluid");
    }

    @Override
    public boolean supportsModFilter() {
        return true;
    }

    public static String getFluidName(Fluid aFluid) {
        if (aFluid == null) return "";
        return aFluid.getLocalizedName(new FluidStack(aFluid, 0));
    }

    @Override
    public int getKeyIndex() {
        return 0;
    }

    @Override
    public String getKeyStr() {
        return "fluids";
    }

    @Override
    public String[] header() {
        if (!CommonProxy.isGTLoaded) return HEADER;
        return Stream.concat(Arrays.stream(HEADER), Arrays.stream(new String[] { GT_DISPLAY_KEY, TOOLTIPS }))
            .toArray(String[]::new);
    }

    @Override
    public Iterable<Object[]> dumpObject(int mode) {
        LinkedList<Object[]> list = new LinkedList<>();
        Map<String, Fluid> fluids = FluidRegistry.getRegisteredFluids();
        ModFilter filter = ModFilter.current();

        Map<Fluid, String> originalNameMap = new HashMap<>();
        Utils.getEnglishTranslation(() -> {
            for (Fluid fluid : fluids.values()) {
                if (!filter.matchesFluid(fluid)) continue;
                originalNameMap.put(fluid, getFluidName(fluid));
            }
        });

        for (String fluidKey : fluids.keySet()) {
            Fluid fluid = fluids.get(fluidKey);
            if (fluid == null || !filter.matchesFluid(fluid)) continue;
            String originalName = originalNameMap.get(fluid);
            String translatedName = getFluidName(fluid);
            int luminosity = fluid.getLuminosity();
            int density = fluid.getDensity();
            int temperature = fluid.getTemperature();
            int viscosity = fluid.getViscosity();
            boolean isGaseous = fluid.isGaseous();
            boolean canBePlaced = fluid.canBePlacedInWorld();
            // Forge records the original registering mod; the GT display item is only a renderer.
            String registeredName = FluidRegistry.getDefaultFluidName(fluid);
            int separator = registeredName == null ? -1 : registeredName.indexOf(':');
            String registeredBy = separator > 0 ? registeredName.substring(0, separator) : "";
            if (!CommonProxy.isGTLoaded) {
                list.add(
                    new Object[] { "fluid." + fluidKey, originalName, translatedName, luminosity, density, temperature,
                        viscosity, isGaseous, canBePlaced, registeredBy });
                continue;
            }
            int id = fluid.getID();
            ItemStack gtDisplay = new ItemStack(ItemList.Display_Fluid.getItem(), 1, id);
            String gtDisplayKey = Utils.getItemKey(gtDisplay);
            String tooltips = Utils.getTooltip(gtDisplay);
            list.add(
                new Object[] { "fluid." + fluidKey, originalName, translatedName, luminosity, density, temperature,
                    viscosity, isGaseous, canBePlaced, registeredBy, gtDisplayKey, tooltips });
        }
        return list;
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation("nei.options.tools.dump.gtnhdumper.gtmaterial.fluid", file.getPath());
    }
}
