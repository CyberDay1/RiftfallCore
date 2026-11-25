package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.network.SkillRequestPayload;
import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.cyberday1.riftfall.network.StatAllocatePayload;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.stat.RiftStats;
import com.cyberday1.riftfall.stat.StatBlock;
import com.cyberday1.riftfall.stat.StatService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The skill screen's server half (V5): builds what the screen shows and answers its requests by
 * calling {@link SkillService}'s verbs, never a copy of their rules. Pushes a fresh view after
 * every request and after every reload, so a changed table reaches an open screen.
 */
public final class SkillScreenService {

    private SkillScreenService() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SkillScreenService::onDatapackSync);
    }

    /**
     * The class's skills and passives, base first, then each ascendancy by name; none before a class
     * is chosen. Within a group castables come first, then the class's passives, then universal ones.
     */
    public static SkillScreenPayload view(SkillBook book, SkillTable table, int level, boolean inRift,
                                          String action, String result) {
        List<SkillScreenPayload.Entry> entries = new ArrayList<>();
        if (book.hasClass()) {
            Stream.concat(table.byId().values().stream(), table.passives().values().stream())
                    .filter(def -> def.universal() || def.skillClass().equals(book.skillClass()))
                    .sorted(Comparator.comparing((SkillBook.Purchase def) -> def.isAscendancy())
                            .thenComparing(def -> def.isAscendancy() ? def.ascendancy() : "")
                            .thenComparing(def -> def.kind() == SkillBook.Kind.PASSIVE)
                            .thenComparing(SkillBook.Purchase::universal))
                    .forEach(def -> {
                        int rank = book.rank(def.id());
                        int max = def.kind().maxRank();
                        double cost = def instanceof SkillDef castable ? castable.costAt(Math.min(rank, max)) : 0.0;
                        entries.add(new SkillScreenPayload.Entry(def.id(), def.isAscendancy() ? def.ascendancy() : "",
                                def.kind() == SkillBook.Kind.ULTIMATE, def.kind() == SkillBook.Kind.PASSIVE, rank, max,
                                cost, book.checkSpend(def, level).name(), def.universal(), facts(def, rank)));
                    });
        }
        List<String> loadout = new ArrayList<>();
        for (int slot = 0; slot < SkillBook.SLOTS; slot++) {
            String id = book.slot(slot);
            loadout.add(id == null ? "" : id);
        }
        SkillBook.Ascension ascension = book.ascension();
        return new SkillScreenPayload(book.hasClass() ? book.skillClass() : "",
                ascension == null ? "" : ascension.name(), level, book.available(level), inRift,
                book.ultimateOpen(), entries, loadout, action, result);
    }

    /** The node's own numbers at {@code rank}, for the detail page. Ranks past the node's top read as its top. */
    static SkillScreenPayload.Facts facts(SkillBook.Purchase def, int rank) {
        return switch (def) {
            case SkillDef castable -> {
                SkillNode node = castable.cast();
                int max = node.maxRank();
                int now = Math.min(rank, max);
                int next = Math.min(now + 1, max);
                double[] rates = SkillTable.LeverType.rates(node.lever());
                yield new SkillScreenPayload.Facts(node.ability().toString(), node.engine().name().toLowerCase(Locale.ROOT),
                        node.powerAttribute().toString(), SkillTable.LeverType.of(node.lever()).key(), rates[0], rates[1],
                        SkillRanks.cooldownTicks(node, now), SkillRanks.cooldownTicks(node, next),
                        SkillRanks.cooldownTicks(node, max), castable.costAt(next), castable.costAt(max), "", 0.0,
                        node.powerMultiplier());
            }
            case PassiveDef passive -> SkillScreenPayload.Facts.passive(
                    (passive.grantsSpell() ? passive.spell() : passive.attribute()).toString(), passive.node().bonusPerRank(),
                    passive.node().bonusBase());
            default -> SkillScreenPayload.Facts.NONE;
        };
    }

    public static SkillScreenPayload view(ServerPlayer player, String action, String result) {
        SkillScreenPayload view = view(SkillService.bookOf(player), SkillDefs.table(), SkillService.levelOf(player),
                RiftLevels.isRift(player.level().dimension()), action, result);
        RiftStats stats = RiftStats.of(player.server);
        return withTier(withAttributes(withRespec(SkillGuideService.enrich(player, view), SkillService.quoteSkills(player),
                SkillService.quoteStats(player)), StatService.available(stats, player.getUUID()),
                stats.allocFor(player.getUUID())), RiftLevels.tier(player.serverLevel()));
    }

    /** The tier hits on the player are reckoned at, for the sheet's Resist. */
    static SkillScreenPayload withTier(SkillScreenPayload view, int tier) {
        return new SkillScreenPayload(view.skillClass(), view.ascension(), view.level(), view.available(),
                view.inRift(), view.ultimateOpen(), view.entries(), view.loadout(), view.action(), view.result(),
                view.skillRespec(), view.statRespec(), view.attributePoints(), view.allocated(), tier);
    }

    static SkillScreenPayload withRespec(SkillScreenPayload view, Respec.Quote skills, Respec.Quote stats) {
        return new SkillScreenPayload(view.skillClass(), view.ascension(), view.level(), view.available(),
                view.inRift(), view.ultimateOpen(), view.entries(), view.loadout(), view.action(), view.result(),
                flat(skills), flat(stats), view.attributePoints(), view.allocated());
    }

    /** The attribute pool and the player's own points per primary, in {@link StatBlock.Primary} order. */
    static SkillScreenPayload withAttributes(SkillScreenPayload view, int available, StatBlock alloc) {
        List<Integer> allocated = new ArrayList<>();
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            allocated.add(alloc.points(primary));
        }
        return new SkillScreenPayload(view.skillClass(), view.ascension(), view.level(), view.available(),
                view.inRift(), view.ultimateOpen(), view.entries(), view.loadout(), view.action(), view.result(),
                view.skillRespec(), view.statRespec(), available, allocated);
    }

    /** The sheet's Confirm: one batch through {@link StatService}, then a fresh view. Public for the rig. */
    public static SkillScreenPayload allocate(ServerPlayer player, StatAllocatePayload request) {
        SkillScreenPayload view = view(player, "ALLOCATE", StatService.allocate(player, request.asMap()).name());
        send(player, view);
        return view;
    }

    static SkillScreenPayload.Respec flat(Respec.Quote quote) {
        return new SkillScreenPayload.Respec(quote.result().name(), quote.cost(), quote.shortBy(), quote.refund());
    }

    /** The serverbound handler's body, public so the rig can drive it with a fake player. */
    public static SkillScreenPayload handle(ServerPlayer player, SkillRequestPayload request) {
        String result = switch (request.kind()) {
            case VIEW -> "";
            case SPEND -> SkillService.spend(player, request.id()).name();
            case EQUIP -> request.id().isEmpty()
                    ? SkillService.unequip(player, request.slot()).name()
                    : SkillService.equip(player, request.slot(), request.id()).name();
            case RESPEC_SKILLS -> SkillService.respecSkills(player).result().name();
            case RESPEC_STATS -> SkillService.respecStats(player).result().name();
            case ASCEND -> SkillService.ascend(player, request.id()).name();
        };
        String action = request.kind() == SkillRequestPayload.Kind.VIEW ? "" : request.kind().name();
        SkillScreenPayload view = view(player, action, result);
        send(player, view);
        return view;
    }

    private static void send(ServerPlayer player, SkillScreenPayload view) {
        // A fake player has no client to tell.
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, view);
        }
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> send(player, view(player, "", "")));
    }
}
