package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.google.gson.JsonObject;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.base.FilteredDataDumper;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.StructureDumpHelper;
import com.iouter.gtnhdumper.common.utils.StructureHacks;
import com.iouter.gtnhdumper.common.utils.Utils;

import blockrenderer6343.client.utils.ConstructableData;
import codechicken.nei.NEIClientUtils;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

public class GTStructuresDumper extends FilteredDataDumper {

    public GTStructuresDumper() {
        super("tools.dump.gtnhdumper.gtstructure");
    }

    @Override
    public String[] header() {
        return null;
    }

    @Override
    public Iterable<String[]> dump(int mode) {
        return null;
    }

    @Override
    public void dumpFile() {
        if (!canDump()) return;
        dumpStructures();
        NEIClientUtils.printChatMessage(dumpMessage(null));
    }

    private void dumpStructures() {
        ModFilter filter = ModFilter.current();
        Map<String, String> failures = new LinkedHashMap<>();
        for (IMetaTileEntity te : GregTechAPI.METATILEENTITIES) {
            if (!(te instanceof IConstructable constructable)) continue;
            String name = te.getClass()
                .getName();
            Path temporary = null;
            try {
                if (!filter.matchesItem(te.getStackForm(1))) continue;
                if (!(constructable.getStructureDefinition() instanceof StructureDefinition<?>definition)) continue;
                // Decode before opening the destination, so a broken structure cannot leave an empty JSON file.
                GTStructure structure = new GTStructure(te, definition, constructable);
                Path output = filter.outputFile(
                    "structures/" + te.getClass()
                        .getSimpleName() + ".json")
                    .toPath();
                Files.createDirectories(output.getParent());
                temporary = Files.createTempFile(output.getParent(), "structure-", ".tmp");
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    GTNHDumper.GSON.toJson(structure, writer);
                }
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                GTNHDumper.info("已写入：" + output.toAbsolutePath());
            } catch (Exception | LinkageError error) {
                failures.put(name, error.toString());
                GTNHDumper.LOG.error("Could not export structure " + name, error);
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException error) {
                        failures.put(name + " temporary file", error.toString());
                        GTNHDumper.LOG.error("Could not remove incomplete structure file " + temporary, error);
                    }
                }
            }
        }
        // Keep failures outside structures/: consumers must not mistake diagnostics for a machine definition.
        Path report = filter.outputFile("structure-failures.json")
            .toPath();
        try {
            Files.createDirectories(report.getParent());
            try (Writer writer = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
                GTNHDumper.GSON.toJson(failures, writer);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not write structure failure report", error);
        }
        if (!failures.isEmpty()) throw new IllegalStateException(
            "Failed to export " + failures.size() + " structures; see structure-failures.json");
    }

    @Override
    public int modeCount() {
        return 1;
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation(
            "nei.options.tools.dump.gtnhdumper.gtstructure.dumped",
            ModFilter.current()
                .outputFile("structures")
                .getPath());
    }

    private static class GTStructure {

        private final int meta;
        private Map<String, String[][]> shapes;
        private final Map<Character, Object> elements = new LinkedHashMap<>();

        public GTStructure(IMetaTileEntity te, StructureDefinition<?> structureDefinition,
            IConstructable constructable) {
            meta = te.getBaseMetaTileEntity()
                .getMetaTileID();
            shapes = StructureDumpHelper.decodeShapes(structureDefinition);
            for (Map.Entry<Character, ? extends IStructureElement<?>> entry : structureDefinition.getElements()
                .entrySet()) {
                Character ch = entry.getKey();
                if (ch >= '퀀' && ch <= '\udfff') {
                    continue;
                }
                if (ch.equals('-')) {
                    continue;
                }
                JsonObject json = new JsonObject();
                @SuppressWarnings("unchecked")
                IStructureElement<IMetaTileEntity> element = (IStructureElement<IMetaTileEntity>) entry.getValue();
                var blocks = StructureHacks
                    .getStacksForElement(te, element, ConstructableData.getTierData(constructable));
                String channel = StructureHacks.getChannel(
                    element.getClass()
                        .getName(),
                    element);
                if (!(channel.isEmpty())) {
                    json.addProperty("channel", channel);
                }
                ItemStack stackTarget = new ItemStack(Item.getItemById(1));
                List<ItemStack> itemStacks;
                if (blocks != null) {
                    itemStacks = new ArrayList<>();
                    blocks.forEach(s -> {
                        if (s != null) {
                            itemStacks.add(s);
                        }
                    });
                    if (!itemStacks.isEmpty()) {
                        stackTarget = itemStacks.get(0);
                        json.add("blocks", GTNHDumper.GSON.toJsonTree(itemStacks));
                    }
                } else {
                    itemStacks = null;
                }
                int dot = StructureDumpHelper.getDotForElement(te, element);
                if (dot != -1) {
                    json.addProperty("dot", dot);
                }
                @SuppressWarnings("unchecked")
                IStructureElement<Object> elementObj = (IStructureElement<Object>) entry.getValue();
                var obj = StructureDumpHelper.scanCandidates(te, elementObj, stackTarget, new BlockPos());
                if (obj instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<ItemStack> stackSet = (Set<ItemStack>) obj;
                    json.add("hatch", GTNHDumper.GSON.toJsonTree(stackSet));
                } else if (obj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<List<ItemStack>> hatchesList = (List<List<ItemStack>>) obj;
                    if (itemStacks != null) {
                        Set<List<ItemStack>> newHatchList = new LinkedHashSet<>();
                        for (var hatches : hatchesList) {
                            boolean isInvaild = false;
                            for (var hatch : hatches) {
                                if ((itemStacks.stream()
                                    .anyMatch(stack -> Utils.isItemEqual(stack, hatch)))) {
                                    isInvaild = true;
                                    break;
                                }
                            }
                            if (!isInvaild) {
                                newHatchList.add(hatches);
                            }
                        }
                        if (!(newHatchList.isEmpty())) {
                            json.add("hatch", GTNHDumper.GSON.toJsonTree(newHatchList));
                        }
                    } else {
                        if (!(hatchesList.isEmpty())) {
                            json.add("hatch", GTNHDumper.GSON.toJsonTree(hatchesList));
                        }
                    }
                }
                if (!json.isEmpty()) elements.put(ch, json);
            }
        }
    }
}
