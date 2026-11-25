package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.RiftfallCore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every line the Fitter's bulk scrap page shows; the lang provider reads {@link #ENGLISH}. Refusals reuse {@link SmithLang}. */
public final class BulkScrapLang {
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".smith.bulk.";
    private static final String MESSAGE = "message." + RiftfallCore.MODID + ".smith.bulk.";

    public static final String OPEN = SCREEN + "open";
    public static final String OPEN_HINT = SCREEN + "open_hint";
    public static final String SCRAP = SCREEN + "scrap";
    public static final String CONFIRM = SCREEN + "confirm";
    public static final String EMPTY = SCREEN + "empty";
    public static final String READY = SCREEN + "ready";
    public static final String ARMED = SCREEN + "armed";
    public static final String PREVIEW = SCREEN + "preview";
    public static final String PREVIEW_NONE = SCREEN + "preview_none";
    public static final String ITEMS = SCREEN + "items";
    public static final String ITEM_ONE = SCREEN + "item_one";
    public static final String OUTPUT = SCREEN + "output";
    public static final String SCRAPPED = MESSAGE + "scrapped";
    public static final String CONFIRM_NEEDED = MESSAGE + "confirm_needed";

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put(OPEN, "Bulk scrap");
        en.put(OPEN_HINT, "Salvage up to 27 items in one go. Shift-click gear into the grid, then press Scrap.");
        en.put(SCRAP, "Scrap");
        en.put(CONFIRM, "Confirm");
        en.put(EMPTY, "Shift-click gear into the grid");
        en.put(READY, "Ready");
        en.put(ARMED, "Epic or rarer gear: press Confirm");
        en.put(PREVIEW, "Scrap %s: %s");
        en.put(PREVIEW_NONE, "Nothing to scrap");
        en.put(ITEMS, "%s items");
        en.put(ITEM_ONE, "1 item");
        en.put(OUTPUT, "%s %s");
        en.put(SCRAPPED, "Scrapped %s");
        en.put(CONFIRM_NEEDED, "Epic or rarer gear: press Confirm within 3 seconds");
        ENGLISH = Collections.unmodifiableMap(en);
    }

    private BulkScrapLang() {}
}
