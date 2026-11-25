package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallEntities;
import com.cyberday1.riftfall.mixin.AccessorMinecraftServer;
import com.cyberday1.riftfall.mixin.InvokerPlayerList;
import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.stat.RiftStats;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.rift.RiftTeleports;
import com.cyberday1.riftfall.skill.StatCommand;
import com.cyberday1.riftfall.worldgen.level.RiftDimensions;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

/** One attempt, one unregistered void world. No LEVEL_STEM entry is ever persisted to level.dat. */
public final class UnseatedArena {
    private static final Logger LOG=LogUtils.getLogger();
    private static final String PREFIX="unseated_attempts/", RETURN="RiftfallUnseatedReturn";
    static final String CONTAINED="RiftfallUnseatedContained", PRACTICED="RiftfallUnseatedPracticed", SEEN="RiftfallUnseatedSeen";
    public static final ResourceKey<DimensionType> TYPE=ResourceKey.create(Registries.DIMENSION_TYPE,RiftfallCore.id("unseated_arena"));
    public static final ResourceLocation TEMPLATE=RiftfallCore.id("boss/containment_court");
    public static final BlockPos ORIGIN=new BlockPos(-50,54,-50);
    public static final Vec3 CENTER=new Vec3(.5,65,.5), ARRIVAL=new Vec3(.5,65,43.5);
    private static final Map<ResourceKey<Level>,Attempt> ATTEMPTS=new HashMap<>();
    private static final Set<UUID> DEFEATED=new HashSet<>();
    // Knocked-out players the shared budget lets back in, and the ticks each still waits at the gate.
    private static final Map<UUID,Integer> RETURNING=new HashMap<>();
    private static final ChunkProgressListener SILENT=new ChunkProgressListener(){
        public void updateSpawnPos(ChunkPos pos){} public void onStatusChange(ChunkPos pos,ChunkStatus status){}
        public void start(){} public void stop(){}
    };
    private static final class Attempt {
        final ServerLevel level;
        final StructureTemplate template;
        final List<UUID> waiting;
        final Set<ChunkPos> tickets=new HashSet<>();
        UnseatedEntity boss;
        int chunk,empty,quiet,victory=-1;
        boolean ready,closing,probe,practice;
        UnseatedRetries retries=new UnseatedRetries();
        CommandSourceStack verification;
        boolean verified;
        Attempt(ServerLevel level,StructureTemplate template,List<UUID> waiting){this.level=level;this.template=template;this.waiting=new ArrayList<>(waiting);}
    }
    private UnseatedArena(){}
    public static void register(){
        NeoForge.EVENT_BUS.addListener(UnseatedArena::tick);
        NeoForge.EVENT_BUS.addListener(UnseatedArena::login);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,UnseatedArena::defeat);
        NeoForge.EVENT_BUS.addListener(UnseatedArena::stopping);
        NeoForge.EVENT_BUS.addListener(UnseatedArena::stopped);
        NeoForge.EVENT_BUS.addListener(UnseatedArena::started);
        NeoForge.EVENT_BUS.addListener(UnseatedArena::bossHurt);
        NeoForge.EVENT_BUS.addListener(UnseatedArena::joined);
        UnseatedArenaProtection.register();
    }
    public static ResourceKey<Level> key(UUID id){return ResourceKey.create(Registries.DIMENSION,RiftfallCore.id(PREFIX+id));}
    public static boolean isArena(ResourceKey<Level> key){
        if(key==null || !key.location().getNamespace().equals(RiftfallCore.MODID))return false;
        String path=key.location().getPath();
        if(!path.startsWith(PREFIX))return false;
        String suffix=path.substring(PREFIX.length());
        try{return UUID.fromString(suffix).toString().equals(suffix);}catch(IllegalArgumentException e){return false;}
    }
    /** The attempt's UUID, from its arena's key; null for any other level. Seeds the god's draw (F6). */
    public static UUID attempt(ResourceKey<Level> key){
        return isArena(key)?UUID.fromString(key.location().getPath().substring(PREFIX.length())):null;
    }
    /** A fresh attempt is prepared before anybody leaves their original world. */
    @SuppressWarnings("deprecation")
    public static int enter(MinecraftServer server,Collection<ServerPlayer> players){return enter(server,players,false);}
    /** {@code practiceOnly}: the trials and nothing else, for anyone who wants them again. */
    @SuppressWarnings("deprecation")
    public static int enter(MinecraftServer server,Collection<ServerPlayer> players,boolean practiceOnly){
        var eligible=players.stream().filter(p->p.isAlive() && !p.isSpectator() && !isArena(p.level().dimension())
                && !RiftLevels.isRift(p.level().dimension()) && !p.getPersistentData().contains(RETURN)
                && ATTEMPTS.values().stream().noneMatch(a->a.waiting.contains(p.getUUID()))).toList();
        if(eligible.isEmpty())throw new IllegalArgumentException("No eligible players. Leave any current rift or arena first.");
        create(server,eligible.stream().map(ServerPlayer::getUUID).toList()).practice=practiceOnly;
        eligible.forEach(p->message(p,UnseatedText.ARENA_PREPARING));
        return eligible.size();
    }
    /** Operator-only headless integration check: constructs, inspects, then drains and deletes a real arena. */
    public static int verify(CommandSourceStack source){
        var a=create(source.getServer(),List.of());a.verification=source;
        source.sendSuccess(()->Component.literal("[unseated-arena] Preparing lifecycle check "+a.level.dimension().location()),false);
        return 1;
    }
    /** Probe only: an attempt nobody crosses into, kept open until {@link #closeProbe}. */
    public static ResourceKey<Level> probe(MinecraftServer server){
        var a=create(server,List.of());a.probe=true;a.retries=new UnseatedRetries(0);return a.level.dimension();
    }
    /** Probe only: the boss once the court is placed, else null. */
    public static UnseatedEntity probeBoss(ResourceKey<Level> key){var a=ATTEMPTS.get(key);return a==null || !a.ready?null:a.boss;}
    /** Probe only: closes through the normal drain and folder deletion. Remove fake players first. */
    public static void closeProbe(ResourceKey<Level> key){var a=ATTEMPTS.get(key);if(a!=null && a.probe && !a.closing)close(a);}
    /** Probe only: lets the court's chunks unload, as if nothing held them, while the attempt stays open. */
    public static void probeUnloadChunks(ResourceKey<Level> key){var a=ATTEMPTS.get(key);if(a!=null && a.probe && !a.closing)release(a);}
    /** Probe only: holds the court's chunks again, so what they saved loads back. */
    public static void probeLoadChunks(ResourceKey<Level> key){var a=ATTEMPTS.get(key);if(a!=null && a.probe && !a.closing)for(int n=0;n<64;n++)force(a,n);}
    /** Probe only: a tether that names the god but that the god does not hold, as a wipe leaves one in an unloaded chunk. */
    public static UUID probeStrayTether(ResourceKey<Level> key){
        var a=ATTEMPTS.get(key);if(a==null || !a.probe || a.boss==null)return null;
        var tether=RiftfallEntities.UNSEATED_TETHER.get().create(a.level);if(tether==null)return null;
        var at=UnseatedEntity.tetherSpot(1);tether.moveTo(at.x,at.y,at.z,0,0);
        tether.rise(a.boss.getUUID(),a.boss.tier(),1);
        return a.level.addFreshEntity(tether)?tether.getUUID():null;
    }
    /** Probe only: locks the scaling from stand-ins, which never cross. */
    public static void probeLock(ResourceKey<Level> key,Collection<? extends ServerPlayer> standIns){
        var a=ATTEMPTS.get(key);if(a!=null && a.probe && a.boss!=null)lock(a.boss,standIns);
    }
    /** The encounter tier of an arena, for player mitigation (C3); 0 when the level is not a prepared arena. */
    public static int tier(ResourceKey<Level> key){var a=ATTEMPTS.get(key);return a==null || a.boss==null?0:a.boss.tier();}
    /** Tier from the highest-level entrant, headcount from who crossed. Nobody can join later. */
    private static void lock(UnseatedEntity boss,Collection<? extends ServerPlayer> entrants){
        boss.lock(UnseatedScaling.tier(entrants.stream().mapToInt(UnseatedArena::levelOf).toArray()),UnseatedScaling.headcount(entrants.size()));
    }
    /** Live from the XP store, as the key forge reads it. */
    private static int levelOf(ServerPlayer p){return RiftXp.levelFor(RiftStats.of(p.server).xpFor(p.getUUID()),RiftXp.LEVEL_CAP);}
    /** The god's effective health, at the point a rift boss's is applied ({@code ThreatEvents#onBossHurt}): after armour. */
    static void bossHurt(LivingDamageEvent.Pre event){
        if(event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        if(event.getEntity() instanceof UnseatedEntity boss)event.setNewDamage(event.getNewDamage()*boss.damageTaken());
        // A tether takes hits on the god's own multiplier, so it is 6% of the god's effective health at any tier and size.
        else if(event.getEntity() instanceof UnseatedTetherEntity tether)event.setNewDamage(event.getNewDamage()*tether.damageTaken());
    }
    /**
     * A reloaded chunk brings the god back as a new instance with the same UUID; the attempt follows it. The old one was
     * only unloaded, and every read of the attempt (tier, phase, victory) belongs to the live one.
     */
    static void joined(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide() || !(event.getEntity() instanceof UnseatedEntity boss))return;
        var a=ATTEMPTS.get(event.getLevel().dimension());
        if(a!=null)a.boss=relink(a.boss,boss);
    }
    /** The god an attempt follows once this one has joined its level: the joiner if it is the same god come back. */
    static UnseatedEntity relink(UnseatedEntity known,UnseatedEntity joined){
        return known!=null && known!=joined && known.getUUID().equals(joined.getUUID())?joined:known;
    }
    /** Ended for good: killed or discarded. A god whose chunk unloaded is still the attempt's and comes back with it. */
    static boolean ended(Entity boss){
        return boss.isRemoved() && boss.getRemovalReason()!=Entity.RemovalReason.UNLOADED_TO_CHUNK;
    }
    /**
     * Whether a tether's god cannot come back: no open attempt in that level, another god, or this one ended. A tether
     * whose god is gone removes itself.
     */
    static boolean bossGone(ResourceKey<Level> key,UUID owner){
        var a=ATTEMPTS.get(key);
        return a==null || a.closing || bossGone(a.boss,owner);
    }
    static boolean bossGone(UnseatedEntity known,UUID owner){
        return known==null || !known.getUUID().equals(owner) || ended(known);
    }
    /** Whether the attempt still exists, i.e. has not finished draining. */
    public static boolean open(ResourceKey<Level> key){return ATTEMPTS.containsKey(key);}
    @SuppressWarnings("deprecation")
    private static Attempt create(MinecraftServer server,List<UUID> players){
        var template=server.overworld().getStructureManager().get(TEMPLATE)
                .orElseThrow(()->new IllegalStateException("Containment court structure is missing."));
        var settings=new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct()),
                server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(RiftDimensions.RIFT_BIOME),List.of());
        settings.updateLayers(); // zero layers, zero features, zero structure sets: only the authored court exists
        var stem=new LevelStem(server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(TYPE),new FlatLevelSource(settings));
        var data=server.getWorldData();
        var level=new ServerLevel(server,Util.backgroundExecutor(),((AccessorMinecraftServer)server).getStorageSource(),
                new DerivedLevelData(data,data.overworldData()),key(UUID.randomUUID()),stem,SILENT,false,0,List.of(),false,new RandomSequences(0));
        server.forgeGetWorldMap().put(level.dimension(),level);server.markWorldsDirty();
        var attempt=new Attempt(level,template,players);ATTEMPTS.put(level.dimension(),attempt);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        return attempt;
    }
    private static void prepare(Attempt a){
        // Two chunks per server tick; players remain at home until all 64 chunks have been placed.
        for(int n=0;n<2 && a.chunk<64;n++,a.chunk++){
            int x=a.chunk%8-4,z=a.chunk/8-4;
            force(a,a.chunk);
            a.level.getChunk(x,z);
            var bounds=new BoundingBox(x*16,a.level.getMinBuildHeight(),z*16,x*16+15,a.level.getMaxBuildHeight()-1,z*16+15);
            var settings=new StructurePlaceSettings().setIgnoreEntities(true).setBoundingBox(bounds)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
            a.template.placeInWorld(a.level,ORIGIN,ORIGIN,settings,a.level.random,2);
        }
        if(a.chunk<64)return;
        a.boss=RiftfallEntities.UNSEATED.get().create(a.level);
        if(a.boss==null)throw new IllegalStateException("Unseated entity could not be created");
        a.boss.moveTo(CENTER.x,CENTER.y,CENTER.z,0,0);
        if(!a.level.addFreshEntity(a.boss))throw new IllegalStateException("Unseated spawn was refused");
        a.boss.practiceOnly(a.practice);
        a.ready=true;
        List<ServerPlayer> crossed=new ArrayList<>();
        for(UUID id:a.waiting){
            var player=a.level.getServer().getPlayerList().getPlayer(id);
            if(player==null || !player.isAlive() || isArena(player.level().dimension()) || RiftLevels.isRift(player.level().dimension()))continue;
            CompoundTag origin=new CompoundTag();origin.putString("dimension",player.level().dimension().location().toString());
            origin.putDouble("x",player.getX());origin.putDouble("y",player.getY());origin.putDouble("z",player.getZ());
            origin.putFloat("yaw",player.getYRot());origin.putFloat("pitch",player.getXRot());
            player.getPersistentData().put(RETURN,origin);
            // Save the return address before crossing: a crash can never strand a player in an expired UUID world.
            ((InvokerPlayerList)a.level.getServer().getPlayerList()).riftfall$savePlayer(player);
            teleport(player,a.level,ARRIVAL,180,0);
            if(player.level()!=a.level){player.getPersistentData().remove(RETURN);continue;}
            crossed.add(player);
            message(player,a.practice?UnseatedText.ARENA_PRACTICE_ARRIVAL:UnseatedText.ARENA_ARRIVAL);
            if(!a.practice)message(player,UnseatedText.ARENA_DEFEAT_RULE);
        }
        lock(a.boss,crossed);
    }
    /** Holds one of the court's 64 chunks, numbered row by row from (-4, -4). */
    private static void force(Attempt a,int n){
        int x=n%8-4,z=n/8-4;a.level.setChunkForced(x,z,true);a.tickets.add(new ChunkPos(x,z));
    }
    private static void tick(ServerTickEvent.Post event){
        var server=event.getServer();
        for(UUID id:List.copyOf(DEFEATED)){
            var player=server.getPlayerList().getPlayer(id);
            if(player==null || leave(player))DEFEATED.remove(id);
        }
        for(var waiting:List.copyOf(RETURNING.entrySet())){
            var player=server.getPlayerList().getPlayer(waiting.getKey());
            var a=player==null?null:ATTEMPTS.get(player.level().dimension());
            // Left, logged out, or the attempt ended: the wait ends with it.
            if(a==null || a.closing){RETURNING.remove(waiting.getKey());continue;}
            int left=waiting.getValue();
            if(left==UnseatedRetries.WAIT || !atGate(player.position()))teleport(player,a.level,ARRIVAL,180,0);
            player.invulnerableTime=Math.max(player.invulnerableTime,20);
            if(left%20==0)player.displayClientMessage(Component.translatable(UnseatedText.ARENA_REENTRY_WAIT,left/20),true);
            if(--left>0)RETURNING.put(waiting.getKey(),left);
            else{RETURNING.remove(waiting.getKey());message(player,UnseatedText.ARENA_REENTRY_READY);}
        }
        for(Attempt a:List.copyOf(ATTEMPTS.values())){
            if(a.closing){drain(a);continue;}
            if(!a.ready){
                if(a.waiting.isEmpty() && a.verification==null && !a.probe){close(a);continue;}
                try{prepare(a);}catch(RuntimeException e){
                    LOG.error("[unseated-arena] FAIL preparing {}",a.level.dimension().location(),e);
                    if(a.verification!=null)a.verification.sendFailure(Component.literal("[unseated-arena] FAIL preparation: "+e.getMessage()));
                    for(UUID id:a.waiting){var p=server.getPlayerList().getPlayer(id);if(p!=null)message(p,UnseatedText.ARENA_FAILED);}
                    close(a);
                }
                continue;
            }
            if(a.verification!=null){inspect(a);close(a);continue;}
            for(var player:List.copyOf(a.level.players())){
                if(atExit(player.position()))leave(player);
                else if(player.getY()<59 || player.position().distanceToSqr(CENTER)>100*100){
                    // A fall is recoverable and cannot drop equipment into a world scheduled for deletion.
                    teleport(player,a.level,rescue(player.position()),player.getYRot(),0);
                    player.hurt(a.level.damageSources().fall(),4);
                }else if(aboveCeiling(player.position()) && !player.isCreative() && !player.isSpectator()){
                    // Soft ceiling: flight is allowed but pointless (D10). Back to the floor, no fall damage.
                    teleport(player,a.level,rescue(player.position()),player.getYRot(),player.getXRot());
                    player.displayClientMessage(Component.translatable(UnseatedText.ARENA_CEILING),true);
                }
            }
            if(a.level.players().isEmpty()){if(!a.probe && ++a.empty>=200)close(a);continue;}a.empty=0;
            if(ended(a.boss)){close(a);continue;}
            if(a.boss.phase()==UnseatedEncounter.Phase.CONTAINED){
                if(a.victory<0){
                    a.victory=400;
                    // D2: the tier locked when the entrants crossed.
                    int tier=a.boss.tier();
                    for(var p:a.level.players())contained(p,a.boss.boundByBinding(),w->StatCommand.awardUnseated(w,tier));
                }
                if(--a.victory<=0)close(a);
            }
            if(server.getTickCount()%10==0)anchorEffects(a);
        }
        // Leaving through another mod's travel ability also ends ownership of the old return address.
        for(var player:server.getPlayerList().getPlayers())
            if(!isArena(player.level().dimension()) && !DEFEATED.contains(player.getUUID()))player.getPersistentData().remove(RETURN);
    }
    /**
     * One player's victory: the flag in the sub-tag a death keeps, then the ten attribute points and the XP once, and only when
     * the binding contained the god (D4: not an operator's phase command).
     */
    static void contained(ServerPlayer p,boolean bound,Predicate<ServerPlayer> award){
        CompoundTag kept=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        kept.putBoolean(CONTAINED,true);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,kept);
        message(p,UnseatedText.ARENA_CONTAINED);
        if(bound)award.test(p);
    }
    public static boolean hasContained(ServerPlayer p){return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(CONTAINED);}
    /** Whether this player has finished the practice round before; kept in the sub-tag a death keeps. */
    public static boolean hasPracticed(ServerPlayer p){return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(PRACTICED);}
    static void practiced(ServerPlayer p){
        CompoundTag kept=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        kept.putBoolean(PRACTICED,true);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,kept);
    }
    /** Whether this player has met an attack added after the trials (F6, F7); until then it does them no harm (F8). */
    public static boolean hasSeen(ServerPlayer p,UnseatedEncounter.Action action){
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(SEEN).getBoolean(seenKey(action));
    }
    public static void seen(ServerPlayer p,UnseatedEncounter.Action action){
        CompoundTag kept=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag seen=kept.getCompound(SEEN);seen.putBoolean(seenKey(action),true);
        kept.put(SEEN,seen);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,kept);
    }
    private static String seenKey(UnseatedEncounter.Action action){return action.name().toLowerCase(Locale.ROOT);}
    /** Whether a knocked-out player is waiting at this arena's gate to come back; the god holds its state meanwhile. */
    public static boolean returning(ResourceKey<Level> key){
        if(RETURNING.isEmpty())return false;
        var a=ATTEMPTS.get(key);if(a==null)return false;
        for(var p:a.level.players())if(RETURNING.containsKey(p.getUUID()))return true;
        return false;
    }
    /** The arrival pad a returning player waits on, short of the lit gate so the wait never walks them home. */
    static boolean atGate(Vec3 pos){return Math.hypot(pos.x-ARRIVAL.x,pos.z-ARRIVAL.z)<=3 && Math.abs(pos.y-ARRIVAL.y)<4;}
    static boolean atExit(Vec3 pos){return Math.abs(pos.x-.5)<=3 && pos.z>=47 && pos.z<=50.5 && Math.abs(pos.y-65)<4;}
    static boolean aboveCeiling(Vec3 pos){return pos.y-CENTER.y>UnseatedEncounter.COURT_CEILING;}
    static Vec3 rescue(Vec3 pos){
        Vec3 d=new Vec3(pos.x-CENTER.x,0,pos.z-CENTER.z).normalize();
        return new Vec3(CENTER.x+d.x*23,65,CENTER.z+d.z*23);
    }
    private static void inspect(Attempt a){
        try{
            if(!(a.level.getServer().getPlayerList() instanceof InvokerPlayerList))throw new IllegalStateException("Return-address save hook not applied");
            if(!a.level.dimensionTypeRegistration().is(TYPE) || a.boss==null || a.boss.isRemoved())throw new IllegalStateException("Dimension type or entity missing");
            for(int x=-26;x<=26;x++)for(int z=-26;z<=26;z++)if(Math.hypot(x,z)<=26){
                if(a.level.getBlockState(new BlockPos(x,64,z)).isAir())throw new IllegalStateException("Missing floor at "+x+","+z);
                for(int y=65;y<84;y++)if(!a.level.getBlockState(new BlockPos(x,y,z)).isAir())throw new IllegalStateException("Obstructed combat lane");
            }
            if(a.level.getBlockState(BlockPos.containing(ARRIVAL).below()).isAir())throw new IllegalStateException("Missing arrival pad");
            // F7: every seat holds the god's body over floor and clear air, and the tethers rise on floor at the bridgeheads.
            for(int n=0;n<UnseatedEncounter.SEATS;n++){
                a.boss.placeOnSeat(n);var s=UnseatedEncounter.seat(n);
                if(a.boss.position().subtract(CENTER.add(s[0],0,s[1])).horizontalDistance()>1e-6)throw new IllegalStateException("Seat "+n+" refused the god");
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
                    var cell=BlockPos.containing(a.boss.getX()+x,64,a.boss.getZ()+z);
                    if(a.level.getBlockState(cell).isAir())throw new IllegalStateException("Seat "+n+" has no floor at "+cell);
                    for(int y=65;y<65+UnseatedEncounter.LANE_HEIGHT;y++)if(!a.level.getBlockState(cell.atY(y)).isAir())throw new IllegalStateException("Seat "+n+" is obstructed at "+cell.atY(y));
                }
            }
            for(int anchor=0;anchor<UnseatedEncounter.SEATS;anchor++){
                double angle=anchor*Math.PI/2;
                var cell=BlockPos.containing(CENTER.x+Math.sin(angle)*UnseatedEncounter.TETHER_RADIUS,64,CENTER.z+Math.cos(angle)*UnseatedEncounter.TETHER_RADIUS);
                if(a.level.getBlockState(cell).isAir() || !a.level.getBlockState(cell.above()).isAir())throw new IllegalStateException("Tether spawn "+anchor+" is not open floor");
            }
            for(var phase:UnseatedEncounter.Phase.values()){
                a.boss.developmentPhase(phase);if(a.boss.phase()!=phase || !a.boss.isAlive())throw new IllegalStateException("Phase failed: "+phase);
                if(phase==UnseatedEncounter.Phase.ASCENT && a.boss.tethersStanding()!=UnseatedEncounter.tethers(a.boss.headcount()))
                    throw new IllegalStateException("Ascent raised "+a.boss.tethersStanding()+" tethers");
            }
            a.verified=true;
        }catch(RuntimeException e){LOG.error("[unseated-arena] FAIL inspection",e);a.verification.sendFailure(Component.literal("[unseated-arena] FAIL inspection: "+e.getMessage()));}
    }
    private static void anchorEffects(Attempt a){
        int phase=a.boss.phase().ordinal();
        for(int n=0;n<4;n++){
            double angle=n*Math.PI/2;
            if(phase>=n || phase>=UnseatedEncounter.Phase.BINDING.ordinal())
                a.level.sendParticles(ParticleTypes.END_ROD,.5+Math.sin(angle)*34,65.2,.5+Math.cos(angle)*34,5,1,.2,1,.01);
        }
        a.level.sendParticles(ParticleTypes.REVERSE_PORTAL,.5,67,48.5,8,1.5,1.5,.1,.01);
    }
    static void defeat(LivingDeathEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player) || !isArena(player.level().dimension()))return;
        // Knockout, not a corpse/respawn in an expiring dimension. Other death handlers never see an uncancelled death.
        event.setCanceled(true);
        var a=ATTEMPTS.get(player.level().dimension());
        boolean live=a!=null && a.ready && !a.closing && a.boss.phase()!=UnseatedEncounter.Phase.CONTAINED;
        if(knockout(player,live?a.retries:null))RETURNING.put(player.getUUID(),UnseatedRetries.WAIT);
        else DEFEATED.add(player.getUUID());
    }
    /**
     * One knockout. While the attempt's shared budget has a charge the player goes back to the gate at half health
     * and returns (true); without one they are sent home at a quarter, as before. Nothing outside the fight is lost.
     */
    static boolean knockout(ServerPlayer player,UnseatedRetries budget){
        boolean back=budget!=null && budget.spend();
        player.setHealth(Math.max(1,player.getMaxHealth()*(back?UnseatedRetries.RETURN_HEALTH:.25f)));player.invulnerableTime=60;
        player.clearFire();player.fallDistance=0;
        player.sendSystemMessage(com.cyberday1.riftfall.lore.CoreTileBeats.voice(UnseatedText.GOD_DEFEAT));
        if(back)player.sendSystemMessage(Component.translatable(UnseatedText.ARENA_REENTRY,UnseatedRetries.WAIT/20,budget.left()));
        else message(player,UnseatedText.ARENA_REJECTED);
        return back;
    }
    public static boolean leave(ServerPlayer player){
        for(var a:ATTEMPTS.values())if(!a.ready)a.waiting.remove(player.getUUID());
        if(!isArena(player.level().dimension()) && !player.getPersistentData().contains(RETURN))return true;
        var server=player.getServer();if(server==null)return false;
        var tag=player.getPersistentData().getCompound(RETURN);
        ResourceLocation id=ResourceLocation.tryParse(tag.getString("dimension"));
        ServerLevel target=id==null?null:server.getLevel(ResourceKey.create(Registries.DIMENSION,id));
        Vec3 pos;
        if(target==null || isArena(target.dimension()) || RiftLevels.isRift(target.dimension())){
            target=server.overworld();var spawn=target.getSharedSpawnPos();
            pos=new Vec3(spawn.getX()+.5,target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,spawn.getX(),spawn.getZ())+1,spawn.getZ()+.5);
        }else pos=new Vec3(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z"));
        teleport(player,target,pos,tag.getFloat("yaw"),tag.getFloat("pitch"));
        if(player.level()!=target)return false; // a travel veto must never be followed by world deletion
        player.getPersistentData().remove(RETURN);((InvokerPlayerList)server.getPlayerList()).riftfall$savePlayer(player);return true;
    }
    private static void teleport(ServerPlayer player,ServerLevel level,Vec3 pos,float yaw,float pitch){
        player.stopRiding();player.clearFire();player.fallDistance=0;player.setDeltaMovement(Vec3.ZERO);
        RiftTeleports.permit(player,level);player.teleportTo(level,pos.x,pos.y,pos.z,yaw,pitch);
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer p && (p.getPersistentData().contains(RETURN) || isArena(p.level().dimension())) && !leave(p))DEFEATED.add(p.getUUID());
    }
    private static void message(ServerPlayer p,String key){p.sendSystemMessage(Component.translatable(key));}
    private static void close(Attempt a){
        a.closing=true;
        for(var p:List.copyOf(a.level.players()))leave(p);
        release(a);
    }
    private static void release(Attempt a){
        for(var c:a.tickets)a.level.setChunkForced(c.x,c.z,false);a.tickets.clear();
    }
    @SuppressWarnings("deprecation")
    private static void drain(Attempt a){
        for(var p:List.copyOf(a.level.players()))leave(p);
        if(!a.level.players().isEmpty())return;
        a.level.getChunkSource().removeTicketsOnClosing();
        a.quiet=a.level.getChunkSource().chunkMap.hasWork()?0:a.quiet+1;
        if(a.quiet<2)return;
        var server=a.level.getServer();a.level.noSave=true;
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(a.level));
        server.forgeGetWorldMap().remove(a.level.dimension());server.markWorldsDirty();ATTEMPTS.remove(a.level.dimension());
        try{
            a.level.close();deleteAttempt(server,a.level.dimension());
            if(a.verification!=null && a.verified){
                boolean deleted=!Files.exists(checkedFolder(attemptsRoot(server),a.level.dimension()));
                if(deleted){
                    LOG.info("[unseated-arena] PASS: dimension, 64 staged chunks, clear floor, arrival, boss phases, save hook, drain, unregister and folder deletion.");
                    a.verification.sendSuccess(()->Component.literal("[unseated-arena] PASS: arena lifecycle verified."),false);
                }else {LOG.error("[unseated-arena] FAIL: arena folder still exists");a.verification.sendFailure(Component.literal("[unseated-arena] FAIL: arena folder still exists"));}
            }
        }
        catch(IOException e){LOG.error("Could not close arena; its folder is retained for next boot",e);}
    }
    private static void stopping(ServerStoppingEvent event){
        for(var a:ATTEMPTS.values()){for(var p:List.copyOf(a.level.players()))leave(p);release(a);}
        // Vanilla drains and closes the levels. Closing during ServerStopping can deadlock chunk IO.
    }
    private static void stopped(ServerStoppedEvent event){
        for(var key:List.copyOf(ATTEMPTS.keySet()))deleteAttempt(event.getServer(),key);
        ATTEMPTS.clear();DEFEATED.clear();RETURNING.clear();
    }
    private static void started(ServerStartedEvent event){
        Path root=attemptsRoot(event.getServer());if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))return;
        try(var dirs=Files.list(root)){
            for(Path dir:dirs.toList()){
                try{UUID id=UUID.fromString(dir.getFileName().toString());if(id.toString().equals(dir.getFileName().toString()))deleteAttempt(event.getServer(),key(id));}
                catch(IllegalArgumentException ignored){} // never delete unrelated names
            }
        }catch(IOException e){LOG.warn("Could not sweep expired Unseated arenas",e);}
    }
    private static Path attemptsRoot(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("dimensions/riftfall/unseated_attempts").toAbsolutePath().normalize();}
    static Path checkedFolder(Path root,ResourceKey<Level> key){
        if(!isArena(key))throw new IllegalArgumentException("Not an arena instance");
        Path base=root.toAbsolutePath().normalize();
        Path folder=base.resolve(key.location().getPath().substring(PREFIX.length())).normalize();
        if(!folder.getParent().equals(base))throw new IllegalArgumentException("Arena escaped its root");
        return folder;
    }
    private static void deleteAttempt(MinecraftServer server,ResourceKey<Level> key){
        Path root=attemptsRoot(server),folder=checkedFolder(root,key);
        // Never follow a linked parent or child into somebody else's world data.
        for(Path parent=folder;parent!=null;parent=parent.getParent())if(Files.isSymbolicLink(parent)){LOG.warn("Refusing linked arena folder {}",folder);return;}
        if(!Files.exists(folder,LinkOption.NOFOLLOW_LINKS))return;
        try(var paths=Files.walk(folder)){
            for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);
        }catch(IOException e){LOG.warn("Could not remove expired arena {}",folder,e);}
    }
}
