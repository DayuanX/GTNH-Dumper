package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.iouter.gtnhdumper.automation.ExportSession;
import com.iouter.gtnhdumper.common.base.ModFilterOption;

import codechicken.core.CommonUtils;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameData;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.common.blocks.ItemMachines;

/** A snapshot of the selected mod; empty input preserves the original full export. */
public final class ModFilter {

    private static String cachedInput;
    private static ModFilter cachedFilter;
    private final String modId;
    private final String modName;

    public ModFilter(String input, Map<String, String> mods) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            modId = "";
            modName = "";
            return;
        }
        String match = null;
        for (String id : mods.keySet()) {
            if (id.equalsIgnoreCase(value)) {
                match = id;
                break;
            }
        }
        if (match == null) {
            for (Map.Entry<String, String> entry : mods.entrySet()) {
                if (value.equalsIgnoreCase(entry.getValue())) {
                    if (match != null) throw new IllegalArgumentException("Ambiguous mod name: " + value);
                    match = entry.getKey();
                }
            }
        }
        if (match == null) throw new IllegalArgumentException("Unknown mod: " + value);
        modId = match;
        modName = mods.get(match);
    }

    public static ModFilter current() {
        if (ExportSession.isExporting()) return ExportSession.modFilter();
        String input = ModFilterOption.INSTANCE.value();
        if (cachedFilter != null && java.util.Objects.equals(input, cachedInput)) return cachedFilter;
        ModFilter filter = resolve(input);
        cachedInput = input;
        cachedFilter = filter;
        return filter;
    }

    /** Resolve manual and autorun selections against the same loaded-mod names and IDs. */
    public static ModFilter resolve(String input) {
        Map<String, String> mods = new LinkedHashMap<>();
        mods.put("minecraft", "Minecraft");
        for (ModContainer mod : Loader.instance()
            .getModList()) {
            mods.put(mod.getModId(), mod.getName());
        }
        return new ModFilter(input, mods);
    }

    public String modId() {
        return modId;
    }

    public boolean isActive() {
        return !modId.isEmpty();
    }

    public boolean matchesMod(String owner) {
        return !isActive() || modId.equalsIgnoreCase(owner);
    }

    public boolean matchesMaterialOwner(String owner) {
        return matchesMod(owner) || owner != null && owner.equalsIgnoreCase(modName);
    }

    public boolean matchesItem(ItemStack stack) {
        String registryName = stack == null || stack.getItem() == null ? null
            : GameData.getItemRegistry()
                .getNameForObject(stack.getItem());
        return matchesItem(stack, registryName);
    }

    boolean matchesItem(ItemStack stack, String registryName) {
        if (!isActive()) return true;
        if (stack == null || stack.getItem() == null) return false;
        String specializedOwner = getGregTechMetaTileEntityOwner(stack);
        return specializedOwner != null ? matchesMod(specializedOwner) : matchesOwnedKey(registryName);
    }

    private static String getGregTechMetaTileEntityOwner(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemMachines)) return null;
        int meta = stack.getItemDamage();
        if (meta < 0 || meta >= GregTechAPI.METATILEENTITIES.length) return null;
        IMetaTileEntity metaTileEntity = GregTechAPI.METATILEENTITIES[meta];
        return metaTileEntity == null ? null : ModClassOwner.find(metaTileEntity.getClass());
    }

    public boolean matchesFluid(Fluid fluid) {
        if (!isActive()) return true;
        if (fluid == null) return false;
        // Forge records the registering mod even for fluids without a placeable block.
        return matchesOwnedKey(FluidRegistry.getDefaultFluidName(fluid));
    }

    private boolean matchesOwnedKey(String key) {
        int separator = key == null ? -1 : key.indexOf(':');
        return separator > 0 && matchesMod(key.substring(0, separator));
    }

    public boolean matchesKey(String key) {
        if (!isActive()) return true;
        if (key == null) return false;
        if (key.startsWith("fluid.")) return matchesFluid(FluidRegistry.getFluid(key.substring(6)));
        return matchesOwnedKey(key);
    }

    public boolean matchesOriginalOwner(Iterable<String> owners) {
        if (!isActive()) return true;
        if (owners == null) return false;
        // Later entries are modifiers/copies, not the original registering mod.
        for (String owner : owners) {
            return owner != null && matchesMod(owner);
        }
        return false;
    }

    public String relativeDirectory() {
        if (!isActive()) return "";
        StringBuilder directory = new StringBuilder();
        for (char c : modId.toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-') {
                directory.append(c);
            } else {
                // Escape rather than remove characters, so different registered IDs cannot collide.
                directory.append('%')
                    .append(String.format(java.util.Locale.ROOT, "%04x", (int) c));
            }
        }
        return "filtered/" + directory + "/";
    }

    public File outputFile(String relativePath) {
        if (ExportSession.isExporting()) return new File(ExportSession.outputDirectory(), relativePath);
        return new File(CommonUtils.getMinecraftDir(), "dumps/" + relativeDirectory() + relativePath);
    }
}
