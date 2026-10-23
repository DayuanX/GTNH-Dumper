package com.iouter.gtnhdumper.common.base;

import net.minecraft.util.ChatComponentTranslation;

import com.iouter.gtnhdumper.common.utils.ModFilter;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.config.DataDumper;

public abstract class FilteredDataDumper extends DataDumper {

    protected FilteredDataDumper(String name) {
        super(name);
    }

    public boolean supportsModFilter() {
        return true;
    }

    protected boolean canDump() {
        try {
            if (ModFilter.current()
                .isActive() && !supportsModFilter()) {
                NEIClientUtils.printChatMessage(
                    new ChatComponentTranslation(
                        "nei.options.tools.dump.gtnhdumper.modFilter.unsupported",
                        renderName()));
                return false;
            }
            return true;
        } catch (IllegalArgumentException e) {
            NEIClientUtils.printChatMessage(
                new ChatComponentTranslation(
                    "nei.options.tools.dump.gtnhdumper.modFilter.invalid",
                    ModFilterOption.INSTANCE.value()));
            return false;
        }
    }

    @Override
    public void dumpFile() {
        if (canDump()) super.dumpFile();
    }

    @Override
    public String getFileName(String prefix) {
        return ModFilter.current()
            .relativeDirectory() + super.getFileName(prefix);
    }
}
