package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;
import static com.cyberday1.riftfall.boss.UnseatedEncounter.*;

/** Summon-only encounter prototype; never edits terrain or awards campaign completion. */
public final class UnseatedEntity extends net.minecraft.world.entity.PathfinderMob {
    /** Its own type so a raised shield is no answer: tagged bypasses_shield, bypasses_cooldown and no_knockback, never bypasses_armor. */
    public static final ResourceKey<DamageType> DAMAGE=ResourceKey.create(Registries.DAMAGE_TYPE,RiftfallCore.id("unseated"));
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> LESSON=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FIGHT=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> FACING=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> PREVIOUS_ACTION=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PREVIOUS_PHASE=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> PREVIOUS_START=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> BIND_PROGRESS=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BINDERS=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.INT);
    // F6 and F7: the seat the god sits at or glides to (-1 the centre), where its glide started, and the Crown's marks.
    private static final EntityDataAccessor<Integer> SEAT=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> LANE_FROM=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> MARKS=SynchedEntityData.defineId(UnseatedEntity.class,EntityDataSerializers.COMPOUND_TAG);
    // Apothic's crit (5% x1.5 on every living attacker); the god's attacks never crit (ruled 2026-11-16).
    static final net.minecraft.resources.ResourceLocation CRIT_CHANCE=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("apothic_attributes","crit_chance"),
            CRIT_DAMAGE=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("apothic_attributes","crit_damage");
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable(UnseatedText.NAME),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);
    // Who each part of the current action has hit: once per player per attack, and a combo's two attacks apart.
    private final Map<Action,Set<UUID>> struck=new EnumMap<>(Action.class);
    // Players meeting the current attack for the first time: it does them no harm (F8).
    private final Set<UUID> firstSight=new HashSet<>();
    private boolean engaged;
    // The binding finished the fight, not an operator's phase command (D4).
    private boolean bound;
    private boolean bindingSpoken, rememberSpoken;
    // A practice-only attempt runs the trials and goes dormant again; it never turns live.
    private boolean practiceOnly;
    private int sequence,absence,idle,sinceSeat,lastTethers;
    // Locked when the entrants cross, like a rift boss's mark; a wipe keeps them.
    private int tier=1,headcount=1;
    // Players who started binding with the deliberate crouch-and-touch, and those of them binding this tick.
    private final Set<UUID> binders=new HashSet<>(),bindingNow=new HashSet<>();
    private final UnseatedBinding binding=new UnseatedBinding();
    // Seeded from the attempt on first use; a wipe starts the attempt's order again.
    private UnseatedDraw draw;
    // A binding reset before the draw was made (a reload): the draw opens with the sweep again once it is.
    private boolean reopen;
    private long drawSalt;
    private Action drawLast=Action.IDLE;
    // A bag read from the save, handed to the draw when it is next made: a reload neither skips nor repeats a draw.
    private Phase savedBagPhase;
    private List<Action> savedBag=List.of();
    private final List<Mark> marks=new ArrayList<>();
    // The tethers by UUID, saved with the god: a chunk reload brings back new instances, and these find them.
    private final List<TetherRef> tethers=new ArrayList<>();
    // Where a transition found the god, so it settles onto its seat (or the centre, for the binding) without a jump.
    private double transitionX,transitionZ;
    // Whether the god has left the spot it was placed on; only a god that moved is carried back.
    private boolean moved;

    /** One Crown circle: follows its player until the lock, then stays. Positions are world x and z. */
    private static final class Mark {
        final UUID player;final int entity;double x,z;boolean locked;
        Mark(UUID player,int entity,double x,double z){this.player=player;this.entity=entity;this.x=x;this.z=z;}
    }
    /** A Crown circle as the client draws it. */
    public record CrownMark(int entity,double x,double z,boolean locked){}
    /** One tether: who it is, and the anchor it rose at, whose chunk says whether its absence means it has fallen. */
    private record TetherRef(UUID id,int anchor){
        ChunkPos chunk(){return new ChunkPos(net.minecraft.core.BlockPos.containing(tetherSpot(anchor)));}
    }
    /** Where a tether rises: the anchor's bridgehead on the court floor. */
    static Vec3 tetherSpot(int anchor){
        double a=anchor*Math.PI/2;
        return new Vec3(UnseatedArena.CENTER.x+Math.sin(a)*TETHER_RADIUS,UnseatedArena.CENTER.y,UnseatedArena.CENTER.z+Math.cos(a)*TETHER_RADIUS);
    }

    public UnseatedEntity(EntityType<? extends UnseatedEntity> type,Level level) {
        super(type,level);setNoGravity(true);setPersistenceRequired();xpReward=0;bar.setVisible(false);
    }
    public static AttributeSupplier.Builder attributes() {
        // ATTACK_DAMAGE is not on a PathfinderMob; BossScaling needs it to carry the tier.
        return createMobAttributes().add(Attributes.MAX_HEALTH,UnseatedScaling.MAX_HEALTH).add(Attributes.ARMOR,12)
                .add(Attributes.ATTACK_DAMAGE,UnseatedScaling.ATTACK_DAMAGE)
                .add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.MOVEMENT_SPEED,0)
                .add(Attributes.FOLLOW_RANGE,32);
    }
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);builder.define(PHASE,0);builder.define(ACTION,0);builder.define(START,0L);
        builder.define(LESSON,0);builder.define(FIGHT,false);builder.define(FACING,0f);
        builder.define(PREVIOUS_ACTION,0);builder.define(PREVIOUS_PHASE,0);builder.define(PREVIOUS_START,0L);
        builder.define(BIND_PROGRESS,0f);builder.define(BINDERS,0);
        builder.define(SEAT,-1);builder.define(LANE_FROM,new Vector3f());builder.define(MARKS,new CompoundTag());
    }
    public Phase phase(){return Phase.values()[entityData.get(PHASE)];}
    public Action action(){return Action.values()[entityData.get(ACTION)];}
    public double actionTicks(float partial){return Math.max(0,level().getGameTime()-entityData.get(START)+partial);}
    public boolean engaged(){return level().isClientSide?entityData.get(FIGHT):engaged;}
    public int lesson(){return entityData.get(LESSON);}
    public boolean practicing(){return lesson()>0;}
    /** Shared binding progress, 0 to 1, and how many players are binding; both as of the last server tick. */
    public float bindProgress(){return entityData.get(BIND_PROGRESS);}
    public int bindersNow(){return entityData.get(BINDERS);}
    public int tier(){return tier;}
    public int headcount(){return headcount;}
    /** The seat the god holds or is gliding to, 0 to 3 towards the anchors; -1 is the court centre. */
    public int seat(){return entityData.get(SEAT);}
    /** Where the current glide started, as an offset from the court centre. */
    public double[] laneFrom(){var v=entityData.get(LANE_FROM);return new double[]{v.x,v.z};}
    /** The god's offset from the court centre. Every reach is measured from where it sits. */
    public double[] courtOffset(){return new double[]{getX()-UnseatedArena.CENTER.x,getZ()-UnseatedArena.CENTER.z};}
    /** Pulse end and pull outer radius from where the god sits now (F7). */
    public double reach(){var o=courtOffset();return UnseatedEncounter.reach(Math.hypot(o[0],o[1]));}
    public List<CrownMark> crownMarks(){
        var out=new ArrayList<CrownMark>();
        for(var t:entityData.get(MARKS).getList("m",Tag.TAG_COMPOUND)){
            var m=(CompoundTag)t;out.add(new CrownMark(m.getInt("e"),m.getDouble("x"),m.getDouble("z"),m.getBoolean("l")));
        }
        return out;
    }
    /** Scales the god once for the attempt: the shared rift-boss curves, never the rift-boss register. */
    public void lock(int tier,int headcount) {
        this.tier=Math.max(1,tier);this.headcount=Math.max(1,headcount);BossScaling.scale(this,this.tier);
        clearBinding();silenceCrit();
    }
    /** No crit: chance 0 and damage x1, modifiers stripped. On joining a level (a spawn or a chunk load) and on the lock. */
    void silenceCrit(){silence(attribute(CRIT_CHANCE),0);silence(attribute(CRIT_DAMAGE),1);}
    private AttributeInstance attribute(net.minecraft.resources.ResourceLocation id){
        return net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(id).map(this::getAttribute).orElse(null);
    }
    /** False when the attribute is absent (no Apothic): nothing to silence. */
    static boolean silence(AttributeInstance instance,double base){
        if(instance==null)return false;
        instance.removeModifiers();instance.setBaseValue(base);return true;
    }
    @Override public void onAddedToLevel(){super.onAddedToLevel();if(!level().isClientSide)silenceCrit();}
    /** The fraction of a landed hit the god takes; applied after armour by {@link UnseatedArena#bossHurt}. Half while a tether stands. */
    public float damageTaken(){return UnseatedScaling.damageTaken(tier,headcount,phase()==Phase.ASCENT?tethersStanding():0);}
    /** ASCENT's tethers still standing. One in a chunk that is not loaded still stands; it has not been felled. */
    public int tethersStanding(){
        if(level() instanceof ServerLevel server)tethers.removeIf(t->felled(server,t));
        return tethers.size();
    }
    private static boolean felled(ServerLevel server,TetherRef t){
        if(server.getEntity(t.id()) instanceof UnseatedTetherEntity tether)return !tether.isAlive();
        // Absent from a chunk whose entities are loaded and ticking: killed or discarded. Absent from one that is unloaded,
        // or on its way out (entities hidden, not yet saved): waiting. isNaturalSpawningAllowed is that ticking test.
        var chunk=t.chunk();
        return server.areEntitiesLoaded(chunk.toLong()) && server.isNaturalSpawningAllowed(chunk);
    }
    /** Whether this tether is one of the god's; a tether that is not removes itself. */
    public boolean holdsTether(UUID id){
        for(var t:tethers)if(t.id().equals(id))return true;
        return false;
    }
    public double attackTicks(float partial){return actionTicks(partial)-(practicing()?PRACTICE_LEAD:0);}
    public float attackYaw(){return entityData.get(FACING);}
    public Action previousAction(){return Action.values()[entityData.get(PREVIOUS_ACTION)];}
    public Phase previousPhase(){return Phase.values()[entityData.get(PREVIOUS_PHASE)];}
    public double previousActionTicks(){return Math.max(0,entityData.get(START)-entityData.get(PREVIOUS_START));}
    /** Nobody binding and no progress, sized for the party. A wipe, a reload and each new binding phase start here. */
    private void clearBinding() {
        binders.clear();bindingNow.clear();binding.reset(headcount);entityData.set(BIND_PROGRESS,0f);entityData.set(BINDERS,0);
        // A binding that starts again from nothing opens with the binder-aimed sweep again (ruled 2026-11-16).
        if(phase()==Phase.BINDING){if(draw!=null)draw.reopen();else reopen=true;}
    }
    private UnseatedDraw draw(){
        if(draw==null){
            draw=new UnseatedDraw(UnseatedDraw.seed(UnseatedArena.attempt(level().dimension()))^drawSalt,drawLast,savedBagPhase,savedBag);
            savedBagPhase=null;savedBag=List.of();
            if(reopen){draw.reopen();reopen=false;}
        }
        return draw;
    }
    /** Swaps the resting pose without restarting the wait for the next attack. */
    private void rest(Action next){int waited=idle;begin(next);idle=waited;}
    private void begin(Action next) {
        entityData.set(FIGHT,engaged);
        if(isAttack(next))entityData.set(FACING,getYRot());
        entityData.set(PREVIOUS_ACTION,entityData.get(ACTION));entityData.set(PREVIOUS_START,entityData.get(START));
        entityData.set(PREVIOUS_PHASE,entityData.get(PHASE));
        entityData.set(ACTION,next.ordinal());entityData.set(START,level().getGameTime());struck.clear();firstSight.clear();idle=0;
        if(!marks.isEmpty()){marks.clear();syncMarks();}
        if(next==Action.TRANSITION){var o=courtOffset();transitionX=o[0];transitionZ=o[1];}
    }
    /** What a new attack does as it starts: who meets it for the first time, and the Crown's marks. */
    private void opening(Action next,List<ServerPlayer> players) {
        if(firstSight(next))for(var p:players)if(!UnseatedArena.hasSeen(p,next))firstSight.add(p.getUUID());
        if(parts(next).stream().anyMatch(part->part.action()==Action.CROWN))mark(players);
    }
    /**
     * A first sight is spent once the attack's hit frames have played out, not when it starts: an attack that a reload,
     * a phase change or a wipe cuts short is still unseen.
     */
    private void remember(ServerLevel server) {
        for(UUID id:firstSight)if(server.getPlayerByUUID(id) instanceof ServerPlayer p)UnseatedArena.seen(p,action());
    }
    public void preview(Action next) {
        if(!engaged && phase()!=Phase.CONTAINED)begin(next);
    }
    private void startFight() { if(UnseatedArena.isArena(level().dimension()) && !engaged && phase()==Phase.CLAIM){
        // Practice once: a roster that has all finished the trials goes from the awakening straight to live combat.
        boolean skip=!practiceOnly && level() instanceof ServerLevel server
                && skipsPractice(server.getPlayers(p->!p.isSpectator()).stream().map(UnseatedArena::hasPracticed).toList());
        engaged=true;entityData.set(LESSON,skip?0:-1);begin(Action.AWAKEN);
        say(Component.translatable(skip?UnseatedText.TRIALS_SKIPPED:UnseatedText.TRIALS_BEGIN));godSays(UnseatedText.GOD_BEGIN);
    } }
    public void practiceOnly(boolean only){practiceOnly=only;}
    public boolean practiceOnly(){return practiceOnly;}
    private List<ServerPlayer> opponents() {
        if(!(level() instanceof ServerLevel server))return List.of();
        return server.getPlayers(p->p.isAlive() && !p.isCreative() && !p.isSpectator() && inCourt(p));
    }
    /** Whether an entity stands in the court, measured from the court centre and floor wherever the god is. */
    static boolean inCourt(Entity e) {
        return UnseatedEncounter.inCourt(e.getX()-UnseatedArena.CENTER.x,e.getZ()-UnseatedArena.CENTER.z,height(e));
    }
    private static double height(Entity e){return e.getY()-UnseatedArena.CENTER.y;}
    // Movement is the glide's own position update; nothing else moves the god.
    @Override public void travel(Vec3 travel) { setDeltaMovement(Vec3.ZERO); }
    @Override public boolean isPushable(){return false;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean shouldDropLoot(){return false;}
    @Override public boolean shouldDropExperience(){return false;}

    @Override public boolean hurt(DamageSource source,float amount) {
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return super.hurt(source,amount);
        if(!UnseatedArena.isArena(level().dimension()))return false;
        // Any attacker, not only a player: a hit counts from inside the court, or from a pet whose owner is.
        var attacker=source.getEntity();
        if(attacker!=null && !inCourt(attacker)
                && !(attacker instanceof OwnableEntity pet && pet.getOwner()!=null && inCourt(pet.getOwner())))return false;
        if(level().isClientSide || phase().ordinal()>=Phase.BINDING.ordinal()
                || lesson()!=0 || action()==Action.AWAKEN || action()==Action.TRANSITION
                || !(source.getEntity() instanceof LivingEntity))return false;
        startFight();
        // A first hit wakes the god; the arrival animation is a safe preparation window.
        if(action()==Action.AWAKEN)return false;
        return super.hurt(source,amount);
    }
    @Override protected void actuallyHurt(DamageSource source,float amount) {
        super.actuallyHurt(source,amount);
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || level().isClientSide)return;
        float floor=UnseatedEncounter.floor(phase(),getMaxHealth());
        if(getHealth()<=floor) {
            setHealth(floor);
            Phase previous=phase();
            entityData.set(PHASE,Math.min(Phase.BINDING.ordinal(),phase().ordinal()+1));
            begin(Action.TRANSITION);
            entityData.set(PREVIOUS_PHASE,previous.ordinal());
            enterPhase();
            say(Component.translatable(phase()==Phase.BINDING?UnseatedText.ASCENT_BROKEN:
                    phase()==Phase.FRACTURE?UnseatedText.FRACTURE:UnseatedText.ASCENT));
            godSays(phase()==Phase.BINDING?UnseatedText.GOD_BROKEN:phase()==Phase.FRACTURE?UnseatedText.GOD_FRACTURE:UnseatedText.GOD_ASCENT);
        }
    }
    /** What a new phase brings: tethers rise in the Ascent; the binding clears them and starts from nothing at the centre. */
    private void enterPhase() {
        if(phase()==Phase.ASCENT)raiseTethers();
        if(phase()==Phase.BINDING){clearBinding();dropTethers();setSeat(-1);sinceSeat=0;}
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(!UnseatedArena.isArena(level().dimension())){
            if(!level().isClientSide)player.displayClientMessage(Component.translatable(UnseatedText.ARENA_ONLY),true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if(!player.getItemInHand(hand).isEmpty())return InteractionResult.PASS;
        if(phase()==Phase.BINDING && player.isShiftKeyDown()) {
            if(!level().isClientSide && player.isAlive() && !player.isSpectator() && action()!=Action.TRANSITION) {
                // The one deliberate start. From here on crouching inside the ring binds, with no second touch.
                binders.add(player.getUUID());if(action()==Action.IDLE)rest(Action.BIND);
                player.displayClientMessage(Component.translatable(UnseatedText.HOLD_CROUCH),true);
                if(!bindingSpoken){bindingSpoken=true;godSays(UnseatedText.GOD_BINDING);}
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if(phase()==Phase.CLAIM && !engaged) {
            if(!level().isClientSide)startFight();
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player,hand);
    }
    @Override public void tick() {
        super.tick();setDeltaMovement(Vec3.ZERO);
        tickEncounter();
    }
    void tickEncounter() {
        if(!(level() instanceof ServerLevel server) || !isAlive())return;
        if(engaged && !UnseatedArena.isArena(level().dimension())){resetEncounter();return;}
        int time=(int)attackTicks(0);
        if(!engaged) {
            if(action()!=Action.CONTAINED && action().duration>0 && time>=action().duration)begin(Action.IDLE);
            return;
        }
        var players=opponents();
        // Nobody in the court is a wipe, unless a knocked-out player is on their way back: the god does not heal.
        if(players.isEmpty()) {if(!UnseatedArena.returning(level().dimension()) && ++absence>=200){resetEncounter();return;}} else absence=0;
        bar.setVisible(!players.isEmpty());bar.setProgress(phase()==Phase.BINDING?binding.fraction():getHealth()/getMaxHealth());
        // Refresh the bar after an operator reset of a contained, already tracked entity.
        if(tickCount%20==0) {
            var nearby=server.getPlayers(p->p.distanceToSqr(this)<40*40);
            for(var p:List.copyOf(bar.getPlayers()))if(!nearby.contains(p))bar.removePlayer(p);
            for(var p:nearby)bar.addPlayer(p);
        }
        int standing=phase()==Phase.ASCENT?tethersStanding():0;
        bar.setName(standing>0?Component.translatable(UnseatedText.BAR_ASCENT_TETHERS,standing):UnseatedText.bar(phase()));
        if(standing<lastTethers)say(standing>0?Component.translatable(UnseatedText.TETHER_FELL,standing):Component.translatable(UnseatedText.TETHERS_GONE));
        lastTethers=standing;
        if(lesson()!=0)bar.setName(lesson()<0?Component.translatable(UnseatedText.BAR_TRIALS):
                Component.translatable(UnseatedText.BAR_PRACTICE,lesson(),UnseatedText.attack(action())));
        if(phase()==Phase.BINDING) {
            if(action()==Action.TRANSITION){settle(time);if(time>=action().duration)begin(Action.IDLE);return;}
            if(tickBinding(server))return;
            // No return: the god keeps attacking while it is bound.
        }
        if(action()==Action.IDLE || action()==Action.BIND) {
            if(!players.isEmpty() && ++idle>=idleGap(phase()))nextAction(players);
        } else {
            if(action()==Action.TRANSITION)settle(time);
            telegraph(server,time);
            followMarks(time);
            attack(server,players,time);
            if(practicing() && time==action().activeEnd)practiceFeedback(players);
            if(time==action().activeEnd)remember(server);
            if(time>=action().duration){
                if(lesson()!=0)advanceLesson(players);
                else begin(Action.IDLE);
            }
        }
    }
    /** After the rest: a glide every third attack from the Fracture on, otherwise the next draw from the phase's bag. */
    private void nextAction(List<ServerPlayer> players) {
        var sighted=players.stream().filter(this::hasLineOfSight).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(sighted==null)return;
        if(reseats(phase()) && sinceSeat>=RESEAT_EVERY){reseat(nextSeat(seat(),draw().random()),players);return;}
        Action next=draw().next(phase());drawLast=next;
        var target=target(players,next,sighted);
        Vec3 delta=target.position().subtract(position());
        setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));yBodyRot=getYRot();
        sequence++;if(reseats(phase()))sinceSeat++;
        begin(next);opening(next,players);
    }
    private void reseat(int to,List<ServerPlayer> players) {
        var from=courtOffset();entityData.set(LANE_FROM,new Vector3f((float)from[0],0,(float)from[1]));
        setSeat(to);sinceSeat=0;begin(Action.RESEAT);opening(Action.RESEAT,players);
    }
    private void setSeat(int n){entityData.set(SEAT,n);}
    private void placeAt(double x,double z){setPos(UnseatedArena.CENTER.x+x,getY(),UnseatedArena.CENTER.z+z);}
    /** A transition carries the god onto its seat, or back to the centre for the binding, over its first 40 ticks. Harmless. */
    private void settle(int time) {
        if(!moved)return;
        double[] home=UnseatedEncounter.seat(phase()==Phase.BINDING || phase()==Phase.CONTAINED?-1:seat());
        if(Math.hypot(transitionX-home[0],transitionZ-home[1])<1e-6)return;
        double f=Math.clamp(time/(double)(Action.RESEAT.activeEnd-Action.RESEAT.windup),0,1);
        placeAt(transitionX+(home[0]-transitionX)*f,transitionZ+(home[1]-transitionZ)*f);
    }
    /** One tick of the shared binding. True on the tick it completes and the god is contained. */
    private boolean tickBinding(ServerLevel server) {
        bindingNow.clear();
        for(UUID id:binders) {
            var p=server.getPlayerByUUID(id);
            if(p!=null && p.isAlive() && !p.isSpectator() && p.isShiftKeyDown() && hasLineOfSight(p)
                    && UnseatedBinding.inRing(p.position().subtract(position()).horizontalDistance()))bindingNow.add(id);
        }
        if(binding.tick(bindingNow.size())) {
            entityData.set(PHASE,Phase.CONTAINED.ordinal());engaged=false;bound=true;
            binders.clear();bindingNow.clear();entityData.set(BIND_PROGRESS,1f);entityData.set(BINDERS,0);
            begin(Action.CONTAINED);bar.removeAllPlayers();bar.setVisible(false);
            say(Component.translatable(UnseatedText.CONTAINED));godSays(UnseatedText.GOD_CONTAINED);
            return true;
        }
        entityData.set(BIND_PROGRESS,binding.fraction());entityData.set(BINDERS,bindingNow.size());
        if(binding.fraction()>=.8f && !rememberSpoken){rememberSpoken=true;godSays(UnseatedText.GOD_REMEMBER);}
        if(!bindingNow.isEmpty() && tickCount%4==0)ring(server,4,ParticleTypes.END_ROD);
        // The settle pose shows while anyone binds. Standing up pauses that player and nothing else.
        if(action()==Action.IDLE && !bindingNow.isEmpty())rest(Action.BIND);
        else if(action()==Action.BIND && bindingNow.isEmpty())rest(Action.IDLE);
        return false;
    }
    /** Who the next attack faces: the binding's sweep goes for a random binder, everything else for the nearest player in sight. */
    private ServerPlayer target(List<ServerPlayer> players,Action next,ServerPlayer nearest) {
        if(phase()==Phase.BINDING && next==Action.SWEEP) {
            var bound=players.stream().filter(p->bindingNow.contains(p.getUUID())).toList();
            if(!bound.isEmpty())return bound.get(getRandom().nextInt(bound.size()));
        }
        return nearest;
    }
    /** The Crown's marks (F6): binders first in the binding, then the highest feet, then anyone. */
    private void mark(List<ServerPlayer> players) {
        var random=draw().random();
        var picked=crownTargets(players,UnseatedEntity::height,p->phase()==Phase.BINDING && bindingNow.contains(p.getUUID()),
                UnseatedEncounter.crownMarks(players.size(),random),random);
        for(var p:picked)marks.add(new Mark(p.getUUID(),p.getId(),p.getX(),p.getZ()));
        syncMarks();
    }
    private void syncMarks() {
        var list=new ListTag();
        for(var m:marks){var t=new CompoundTag();t.putInt("e",m.entity);t.putDouble("x",m.x);t.putDouble("z",m.z);t.putBoolean("l",m.locked);list.add(t);}
        var tag=new CompoundTag();tag.put("m",list);entityData.set(MARKS,tag);
    }
    /** Each circle follows its player for 30 ticks and then locks where they stood: the lock is the two-second warning. */
    private void followMarks(int time) {
        if(marks.isEmpty() || !(level() instanceof ServerLevel server))return;
        for(var part:parts(action()))if(part.action()==Action.CROWN) {
            int t=time-part.offset();
            if(t>CROWN_FOLLOW)return;
            for(var m:marks){var p=server.getPlayerByUUID(m.player);if(p!=null && p.isAlive()){m.x=p.getX();m.z=p.getZ();}}
            if(t==CROWN_FOLLOW){for(var m:marks)m.locked=true;syncMarks();}
        }
    }
    private void practiceFeedback(List<ServerPlayer> players){
        for(var player:players){
            var feedback=action()==Action.PULL?Component.translatable(UnseatedText.PRACTICE_PULL):
                    struckThisAction(player.getUUID())?Component.translatable(UnseatedText.PRACTICE_CAUGHT,UnseatedText.response(action())):Component.translatable(UnseatedText.PRACTICE_AVOIDED);
            player.displayClientMessage(feedback,true);
        }
    }
    private void advanceLesson(List<ServerPlayer> players){
        int next=lesson()<0?1:lesson()+1;
        if(next>3){
            for(var player:players)UnseatedArena.practiced(player);
            if(practiceOnly){resetEncounter();say(Component.translatable(UnseatedText.PRACTICE_ONLY_COMPLETE));return;}
            entityData.set(LESSON,0);sequence=0;begin(Action.TRANSITION);
            say(Component.translatable(UnseatedText.PRACTICE_COMPLETE));return;
        }
        entityData.set(LESSON,next);
        var target=players.stream().filter(this::hasLineOfSight).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(target!=null){var d=target.position().subtract(position());setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));yBodyRot=getYRot();}
        begin(lessonAction(next));
    }
    private Set<UUID> struck(Action part){return struck.computeIfAbsent(part,k->new HashSet<>());}
    private void attack(ServerLevel server,List<ServerPlayer> players,int time) {
        double attackDamage=getAttributeValue(Attributes.ATTACK_DAMAGE);
        for(var part:parts(action())) {
            Action a=part.action();int t=time-part.offset();
            if(!a.active(t))continue;
            if(t==a.windup)server.playSound(null,blockPosition(),a==Action.RESEAT?SoundEvents.BEACON_DEACTIVATE:SoundEvents.WARDEN_SONIC_BOOM,SoundSource.HOSTILE,1,.65f);
            float amount=UnseatedScaling.damage(a,phase(),attackDamage);
            switch(a) {
                case SWEEP,PULSE -> {
                    for(var player:players) {
                        var delta=player.position().subtract(position());
                        boolean hit=a==Action.SWEEP?sweepHits(delta.x,delta.z,attackYaw(),height(player)):pulseHits(delta.horizontalDistance(),height(player),t,reach());
                        if(hit && hasLineOfSight(player) && struck(a).add(player.getUUID())) {
                            var force=new Vec3(delta.x,0,delta.z).normalize().scale(.5);
                            strike(player,amount,new Vec3(force.x,.15,force.z));
                        }
                    }
                }
                case PULL -> {
                    double outer=reach();
                    for(var player:players) {
                        Vec3 d=position().subtract(player.position());double radius=d.horizontalDistance();
                        if(radius>PULL_INNER && radius<outer && hasLineOfSight(player)) {
                            // Bounded horizontal pull, no upward launch and no drag through solid walls. Anyone held aloft sinks.
                            Vec3 force=new Vec3(d.x,0,d.z).normalize().scale(.035);
                            Vec3 motion=player.getDeltaMovement();
                            if(motion.horizontalDistance()<.5)player.push(force.x,pullSink(height(player)),force.z);
                            player.hurtMarked=true;
                        }
                    }
                }
                case CROWN -> {
                    // Sharing a circle costs everyone in it more. No push: the column falls where they stand.
                    for(var m:marks) {
                        var inside=players.stream().filter(p->crownHits(p.getX()-m.x,p.getZ()-m.z,height(p))).toList();
                        float shared=(float)(amount*crownShare(inside.size()));
                        for(var player:inside)if(struck(a).add(player.getUUID()))strike(player,shared,Vec3.ZERO);
                    }
                }
                case THRONE_FALL -> {
                    for(var player:players) {
                        var delta=player.position().subtract(position());
                        if(throneHits(delta.x,delta.z,height(player)) && hasLineOfSight(player) && struck(a).add(player.getUUID())) {
                            // Outward, with the sweep's and the pulse's push.
                            var force=new Vec3(delta.x,0,delta.z).normalize().scale(.5);
                            strike(player,amount,new Vec3(force.x,.15,force.z));
                        }
                    }
                }
                case RESEAT -> glide(t,players,amount);
                default -> {}
            }
        }
    }
    /** One tick of the glide: the god moves along its lane, and anyone its body passes is struck once and pushed aside. */
    private void glide(int t,List<ServerPlayer> players,float amount) {
        double[] a=laneFrom(),b=UnseatedEncounter.seat(seat());
        double f0=UnseatedEncounter.glide(t-1),f1=UnseatedEncounter.glide(t);
        double px=a[0]+(b[0]-a[0])*f0,pz=a[1]+(b[1]-a[1])*f0,cx=a[0]+(b[0]-a[0])*f1,cz=a[1]+(b[1]-a[1])*f1;
        placeAt(cx,cz);moved=true;
        double dx=b[0]-a[0],dz=b[1]-a[1],length=Math.hypot(dx,dz);
        for(var player:players) {
            double x=player.getX()-UnseatedArena.CENTER.x,z=player.getZ()-UnseatedArena.CENTER.z;
            if(!laneHits(x,z,px,pz,cx,cz,height(player)) || !struck(Action.RESEAT).add(player.getUUID()))continue;
            // Sideways, away from the lane's middle line: perpendicular to the path, on the player's side of it.
            double sx=length<1e-6?1:-dz/length,sz=length<1e-6?0:dx/length;
            if((x-cx)*sx+(z-cz)*sz<0){sx=-sx;sz=-sz;}
            strike(player,amount,new Vec3(sx*.5,.15,sz*.5));
        }
    }
    /** One landed hit. Practice and first sight cost nothing; a hit on a binder breaks a quarter of the binding. */
    private void strike(ServerPlayer player,float amount,Vec3 push) {
        if(practicing()){
            player.displayClientMessage(Component.translatable(UnseatedText.PRACTICE_ZONE),true);return;
        }
        if(firstSight.contains(player.getUUID())){
            player.displayClientMessage(Component.translatable(UnseatedText.FIRST_SIGHT),true);return;
        }
        // A hit on a binder breaks a quarter of the binding; it does not unseat the binder.
        if(phase()==Phase.BINDING && bindingNow.contains(player.getUUID()))binding.strike();
        player.hurt(new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE),this),amount);
        if(push.lengthSqr()>0)player.push(push.x,push.y,push.z);
        player.hurtMarked=true;
    }
    private void telegraph(ServerLevel server,int time) {
        if(!isAttack(action()))return;
        if(time==(practicing()?1-PRACTICE_LEAD:1)) {
            var line=practicing()?Component.translatable(UnseatedText.TELEGRAPH_PRACTICE,lesson(),UnseatedText.attack(action()),UnseatedText.response(action())):
                    Component.translatable(UnseatedText.TELEGRAPH,UnseatedText.attack(action()),UnseatedText.response(action()));
            // A marked player reads that it is them.
            var marked=new HashSet<UUID>();for(var m:marks)marked.add(m.player);
            for(var p:server.getPlayers(p->p.distanceToSqr(this)<40*40))
                p.displayClientMessage(marked.contains(p.getUUID())?Component.translatable(UnseatedText.CROWN_MARKED):line,true);
            server.playSound(null,blockPosition(),SoundEvents.BEACON_POWER_SELECT,SoundSource.HOSTILE,.8f,.6f);
        }
        // Deliberate countdown, not flashing: three beats before the damaging/force frame.
        if(time==action().windup-20 || time==action().windup-10 || time==action().windup-1)
            server.playSound(null,blockPosition(),SoundEvents.NOTE_BLOCK_HAT.value(),SoundSource.HOSTILE,.65f,time==action().windup-1?1.5f:1f);
        // Ground geometry is drawn locally from the synchronized action clock, even with particles disabled.
    }
    private void ring(ServerLevel server,double radius,net.minecraft.core.particles.SimpleParticleType particle) {
        for(int i=0;i<48;i++){double a=i*Math.PI/24;server.sendParticles(particle,getX()+Math.cos(a)*radius,getY()+.15,getZ()+Math.sin(a)*radius,1,0,0,0,0);}
    }
    private void say(Component message) {
        if(level() instanceof ServerLevel server)for(var p:server.getPlayers(p->p.distanceToSqr(this)<40*40))p.displayClientMessage(message,true);
    }
    /** The god's own voice goes to chat, so the instruction on the action bar is not replaced. */
    private void godSays(String key) {
        if(level() instanceof ServerLevel server)for(var p:server.getPlayers(p->p.distanceToSqr(this)<40*40))
            p.displayClientMessage(com.cyberday1.riftfall.lore.CoreTileBeats.voice(key),false);
    }
    /** ASCENT's tethers at the anchor bridgeheads: min(4, 2 + n/2) for the party that crossed (F7). */
    private void raiseTethers() {
        dropTethers();
        if(!(level() instanceof ServerLevel server))return;
        float taken=UnseatedScaling.damageTaken(tier,headcount);
        for(int anchor:tetherAnchors(UnseatedEncounter.tethers(headcount))) {
            var tether=RiftfallEntities.UNSEATED_TETHER.get().create(server);
            if(tether==null)continue;
            var at=tetherSpot(anchor);
            tether.moveTo(at.x,at.y,at.z,0,0);
            tether.rise(getUUID(),tier,taken);
            if(server.addFreshEntity(tether))tethers.add(new TetherRef(tether.getUUID(),anchor));
        }
        lastTethers=tethers.size();
        if(!tethers.isEmpty())for(var p:server.getPlayers(p->p.distanceToSqr(this)<40*40))
            p.displayClientMessage(Component.translatable(UnseatedText.TETHERS_RISE,tethers.size()),false);
    }
    /** Removes the tethers that are loaded; any in an unloaded chunk find on loading that the god no longer holds them. */
    private void dropTethers(){
        if(level() instanceof ServerLevel server)
            for(var t:tethers)if(server.getEntity(t.id()) instanceof UnseatedTetherEntity tether)tether.discard();
        tethers.clear();lastTethers=0;
    }
    public void resetEncounter() {
        engaged=bound=bindingSpoken=rememberSpoken=false;absence=sequence=idle=sinceSeat=0;clearBinding();entityData.set(LESSON,0);entityData.set(PHASE,0);
        dropTethers();draw=null;reopen=false;drawSalt=0;drawLast=Action.IDLE;savedBagPhase=null;savedBag=List.of();setSeat(-1);
        if(moved){placeAt(0,0);moved=false;}
        begin(Action.IDLE);
        setHealth(getMaxHealth());bar.setVisible(false);
    }
    public void developmentPhase(Phase phase) {
        if(!UnseatedArena.isArena(level().dimension()))return;
        resetEncounter();entityData.set(PHASE,phase.ordinal());
        engaged=phase!=Phase.CONTAINED;
        setHealth(switch(phase){case CLAIM -> getMaxHealth();case FRACTURE -> getMaxHealth()*.70f;
            case ASCENT -> getMaxHealth()*.35f;default -> 1;});
        begin(phase==Phase.CONTAINED?Action.CONTAINED:Action.TRANSITION);
        enterPhase();
    }
    /** Probe only: start an attack now at a fixed heading, skipping the idle and the turn to the nearest player. */
    public boolean forceAttack(Action next,float yaw) {
        if(!engaged || !isAttack(next) || lesson()!=0 || phase()==Phase.CONTAINED
                || !UnseatedArena.isArena(level().dimension()))return false;
        if(next==Action.RESEAT)return forceReseat(nextSeat(seat(),draw().random()));
        setYRot(yaw);yBodyRot=yaw;begin(next);opening(next,opponents());return true;
    }
    /** Probe only: glide to this seat now, from wherever the god is. */
    public boolean forceReseat(int to) {
        if(!engaged || lesson()!=0 || phase()==Phase.CONTAINED || to<0 || to>=SEATS
                || !UnseatedArena.isArena(level().dimension()))return false;
        reseat(to,opponents());return true;
    }
    /** Verification only: the god at a seat, at rest. */
    void placeOnSeat(int n){setSeat(n);var s=UnseatedEncounter.seat(n);placeAt(s[0],s[1]);moved=true;}
    /** Probe only: whether this attack's hit test has selected the player. Cleared when the next action begins. */
    public boolean struckThisAction(UUID player){
        for(var set:struck.values())if(set.contains(player))return true;
        return false;
    }
    /** Probe only: who the Crown marked, in order. */
    public List<UUID> marked(){return marks.stream().map(m->m.player).toList();}
    /** Probe only: ASCENT's tethers still standing and loaded. */
    public List<UnseatedTetherEntity> tethers(){
        tethersStanding();var out=new ArrayList<UnseatedTetherEntity>();
        if(level() instanceof ServerLevel server)
            for(var t:tethers)if(server.getEntity(t.id()) instanceof UnseatedTetherEntity tether)out.add(tether);
        return out;
    }
    /** Whether a player's binding contained the god; an operator's phase command never sets it. */
    public boolean boundByBinding(){return bound;}
    @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);if(phase()!=Phase.CONTAINED)bar.addPlayer(player);}
    @Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);bar.removePlayer(player);}
    @Override public void remove(RemovalReason reason){
        super.remove(reason);if(bar!=null)bar.removeAllPlayers();
        // A chunk unload never comes through here (it sets the reason directly), and must not take the tethers with it.
        if(tethers!=null && reason.shouldDestroy())dropTethers();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putInt("UnseatedPhase",phase().ordinal());tag.putBoolean("UnseatedEngaged",engaged);tag.putInt("UnseatedSequence",sequence);
        tag.putInt("UnseatedLesson",lesson());tag.putBoolean("UnseatedBound",bound);
        tag.putInt("UnseatedTier",tier);tag.putInt("UnseatedHeadcount",headcount);tag.putBoolean("UnseatedPracticeOnly",practiceOnly);
        tag.putInt("UnseatedSeat",seat());tag.putInt("UnseatedSinceSeat",sinceSeat);tag.putString("UnseatedLast",drawLast.name());
        tag.putBoolean("UnseatedBindingSpoken",bindingSpoken);tag.putBoolean("UnseatedRememberSpoken",rememberSpoken);
        Phase bagPhase=draw!=null?draw.phase():savedBagPhase;
        if(bagPhase!=null){
            var bag=new ListTag();for(var a:draw!=null?draw.left():savedBag)bag.add(StringTag.valueOf(a.name()));
            tag.putString("UnseatedBagPhase",bagPhase.name());tag.put("UnseatedBag",bag);
        }
        var held=new ListTag();
        for(var t:tethers){var one=new CompoundTag();one.putUUID("Id",t.id());one.putInt("Anchor",t.anchor());held.add(one);}
        tag.put("UnseatedTethers",held);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);int phase=Math.clamp(tag.getInt("UnseatedPhase"),0,Phase.CONTAINED.ordinal());
        entityData.set(PHASE,phase);engaged=tag.getBoolean("UnseatedEngaged") && phase<Phase.CONTAINED.ordinal();
        entityData.set(LESSON,engaged && phase==0 && tag.getInt("UnseatedLesson")!=0?-1:0);
        sequence=Math.max(0,tag.getInt("UnseatedSequence"));absence=0;
        bound=tag.getBoolean("UnseatedBound") && phase==Phase.CONTAINED.ordinal();
        // The tier's attribute modifiers are permanent and load with the entity; only the two numbers are ours.
        tier=Math.max(1,tag.getInt("UnseatedTier"));headcount=Math.max(1,tag.getInt("UnseatedHeadcount"));
        practiceOnly=tag.getBoolean("UnseatedPracticeOnly");
        // The draw carries on from the saved count, deterministic for the attempt, never repeating the last attack.
        setSeat(tag.contains("UnseatedSeat")?Math.clamp(tag.getInt("UnseatedSeat"),-1,SEATS-1):-1);
        sinceSeat=Math.max(0,tag.getInt("UnseatedSinceSeat"));
        // A god saved on its way back to the centre (the binding's transition) holds no seat but has still moved.
        var offset=courtOffset();moved=seat()>=0 || Math.hypot(offset[0],offset[1])>1e-6;
        try{drawLast=Action.valueOf(tag.getString("UnseatedLast"));}catch(IllegalArgumentException e){drawLast=Action.IDLE;}
        draw=null;reopen=false;drawSalt=sequence*0x9E3779B97F4A7C15L;
        savedBagPhase=null;savedBag=List.of();
        try{
            if(engaged && tag.contains("UnseatedBagPhase")){
                var left=new ArrayList<Action>();
                for(var a:tag.getList("UnseatedBag",Tag.TAG_STRING))left.add(Action.valueOf(a.getAsString()));
                savedBagPhase=Phase.valueOf(tag.getString("UnseatedBagPhase"));savedBag=List.copyOf(left);
            }
        }catch(IllegalArgumentException e){savedBagPhase=null;savedBag=List.of();}
        // The god's own lines are spoken once an attempt; a reload does not repeat them.
        bindingSpoken=engaged && tag.getBoolean("UnseatedBindingSpoken");rememberSpoken=engaged && tag.getBoolean("UnseatedRememberSpoken");
        // Its tethers come back as new instances and are found again by UUID. Only the Ascent has any.
        tethers.clear();
        if(engaged && phase==Phase.ASCENT.ordinal())for(var t:tag.getList("UnseatedTethers",Tag.TAG_COMPOUND)){
            var one=(CompoundTag)t;
            if(one.hasUUID("Id"))tethers.add(new TetherRef(one.getUUID("Id"),Math.clamp(one.getInt("Anchor"),0,SEATS-1)));
        }
        lastTethers=tethers.size();
        clearBinding();
        // Never resume a damaging frame or a half-completed interaction after loading. A transition settles the god onto its seat.
        begin(phase==Phase.CONTAINED.ordinal()?Action.CONTAINED:lesson()!=0?Action.AWAKEN:engaged?Action.TRANSITION:Action.IDLE);
        if(phase==Phase.BINDING.ordinal() || phase==Phase.CONTAINED.ordinal())setHealth(1);
        bar.setVisible(engaged);
    }
}
