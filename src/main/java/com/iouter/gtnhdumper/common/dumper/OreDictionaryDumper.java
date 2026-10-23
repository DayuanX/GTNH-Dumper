package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;

import net.minecraft.util.ChatComponentTranslation;

import com.iouter.gtnhdumper.common.base.WikiDumper;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.Utils;

public class OreDictionaryDumper extends WikiDumper {

    public OreDictionaryDumper() {
        super("tools.dump.gtnhdumper.oreDictionary");
    }

    @Override
    public boolean supportsModFilter() {
        return true;
    }

    @Override
    public int getKeyIndex() {
        return 0;
    }

    @Override
    public String getKeyStr() {
        return "oreDictionaries";
    }

    @Override
    public String[] header() {
        return new String[] { "oreDict", "items" };
    }

    @Override
    public Iterable<Object[]> dumpObject(int mode) {
        LinkedList<Object[]> list = new LinkedList<>();
        Map<String, String[]> map = Utils.getOreDict();
        ModFilter filter = ModFilter.current();
        for (String key : map.keySet()) {
            String[] items = Arrays.stream(map.get(key))
                .filter(filter::matchesKey)
                .toArray(String[]::new);
            if (items.length > 0) list.add(new Object[] { key, items });
        }
        return list;
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation("nei.options.tools.dump.gtnhdumper.oreDictionary.dumped", file.getPath());
    }
}
