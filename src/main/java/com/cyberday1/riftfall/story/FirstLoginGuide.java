package com.cyberday1.riftfall.story;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.skill.ClassSelectService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The login line. A new character's first login is given FTB Quests' book once (GuideBookLedger) and told so; any
 * login while unclassed adds the clickable class command. No screen opens on its own. A character is new while its
 * vanilla play-time stat is 0: it is saved with the player, and it tells apart an old character who never chose a
 * class, got a kit or touched the Core, which a class, the kit ledger or story progress cannot.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class FirstLoginGuide {
    /** Opens the quest screen on use. Looked up by id, so FTB Quests stays off the classpath. */
    public static final ResourceLocation BOOK = ResourceLocation.parse("ftbquests:book");
    public static final String BOOK_GIVEN = "message.riftfall.guide.book";

    private FirstLoginGuide() {}

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        var line = line(giveBook(player), ClassSelectService.shouldOfferFor(player));
        if (line != null) player.sendSystemMessage(line);
    }

    /** Gives a new character the book once; characters from before this update, and a second login, get nothing. */
    public static boolean giveBook(ServerPlayer player) {
        var ledger = GuideBookLedger.of(player.server);
        var item = BuiltInRegistries.ITEM.getOptional(BOOK).orElse(null);
        if (item == null || ledger.has(player.getUUID()) || !newCharacter(player)) return false;
        var stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        ledger.record(player.getUUID());
        return true;
    }

    /** Never played a tick in this world. */
    public static boolean newCharacter(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) == 0;
    }

    static @Nullable Component line(boolean bookGiven, boolean unclassed) {
        MutableComponent line = bookGiven ? Component.translatable(BOOK_GIVEN) : null;
        if (!unclassed) return line;
        return line == null ? ClassSelectService.reminder() : line.append(" ").append(ClassSelectService.reminder());
    }
}
