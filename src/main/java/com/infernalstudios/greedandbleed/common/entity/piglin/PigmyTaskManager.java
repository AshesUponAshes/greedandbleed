package com.infernalstudios.greedandbleed.common.entity.piglin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.infernalstudios.greedandbleed.api.PiglinTaskManager;
import com.infernalstudios.greedandbleed.api.TaskManager;
import com.infernalstudios.greedandbleed.server.loot.GreedAndBleedLootTables;
import com.infernalstudios.greedandbleed.server.registry.MemoryModuleTypeRegistry;
import com.infernalstudios.greedandbleed.server.tasks.AdmiringItemTask;
import com.infernalstudios.greedandbleed.server.tasks.FinishAdmiringItemTask;
import com.infernalstudios.greedandbleed.server.tasks.ForgetAdmiringItemTask;
import com.infernalstudios.greedandbleed.server.tasks.IgnoreAdmiringItemTask;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.BrainUtil;
import net.minecraft.entity.ai.brain.memory.MemoryModuleType;
import net.minecraft.entity.ai.brain.schedule.Activity;
import net.minecraft.entity.ai.brain.task.*;
import net.minecraft.entity.monster.HoglinEntity;
import net.minecraft.entity.monster.piglin.AbstractPiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameterSets;
import net.minecraft.loot.LootParameters;
import net.minecraft.loot.LootTable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

/***
 * An extensible class for initializing and handling a Brain for an PiglinPygmyEntity.
 * This was based on the class PiglinTasks.
 * @author Thelnfamous1
 * @param <T> A class that extends PiglinPygmyEntity
 */
public class PigmyTaskManager<T extends PigmyEntity> extends PiglinTaskManager<T> {

    public PigmyTaskManager(T pygmy, Brain<T> dynamicBrain) {
        super(pygmy, dynamicBrain);
    }

    @Override
    public void initActivities() {
        super.initActivities();
        this.initAdmireItemActivity(10);
        this.initCelebrateActivity(10);
        this.initAvoidActivity(10);
        this.initRideActivity(10);
    }

    @Override
    public Optional<? extends LivingEntity> findNearestValidAttackTarget() {
        if (isNearZombified(this.mob)) {
            return Optional.empty();
        } else {
            Optional<LivingEntity> angryAt = BrainUtil.getLivingEntityFromUUIDMemory(this.mob, MemoryModuleType.ANGRY_AT);
            if (angryAt.isPresent() && isAttackAllowed(angryAt.get())) {
                return angryAt;
            } else {
                if (this.dynamicBrain.hasMemoryValue(MemoryModuleType.UNIVERSAL_ANGER)) {
                    Optional<PlayerEntity> nearestPlayer = this.dynamicBrain.getMemory(MemoryModuleType.NEAREST_VISIBLE_TARGETABLE_PLAYER);
                    if (nearestPlayer.isPresent()) {
                        return nearestPlayer;
                    }
                }

                Optional<MobEntity> nearestNemesis = this.dynamicBrain.getMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
                if (nearestNemesis.isPresent()) {
                    return nearestNemesis;
                } else {
                    Optional<PlayerEntity> nearestPlayerNotWearingGold = this.dynamicBrain.getMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD);
                    return nearestPlayerNotWearingGold.isPresent() && isAttackAllowed(nearestPlayerNotWearingGold.get()) ? nearestPlayerNotWearingGold : Optional.empty();
                }
            }
        }
    }

    @Override
    public void setCoreAndDefault() {
        this.dynamicBrain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        this.dynamicBrain.setDefaultActivity(Activity.IDLE);
    }

    @Override
    public void initMemories() {
        int i = TIME_BETWEEN_HUNTS.randomValue(this.mob.level.random);
        this.dynamicBrain.setMemoryWithExpiry(MemoryModuleType.HUNTED_RECENTLY, true, i);
    }

    @Override
    public ActionResultType mobInteract(PlayerEntity player, Hand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (canPiglinAdmire(this.mob, itemstack)) {
            ItemStack singleton = itemstack.split(1);
            holdInOffhand(this.mob, singleton);
            admireItem(this.mob, 120L);
            stopWalking(this.mob);
            return ActionResultType.CONSUME;
        } else {
            return ActionResultType.PASS;
        }
    }

    @Override
    public void updateActivity() {
        Activity activity = this.dynamicBrain.getActiveNonCoreActivity().orElse(null);
        this.dynamicBrain.setActiveActivityToFirstValid(ImmutableList.of(Activity.ADMIRE_ITEM, Activity.FIGHT, Activity.AVOID, Activity.CELEBRATE, Activity.RIDE, Activity.IDLE));
        Activity activity1 = this.dynamicBrain.getActiveNonCoreActivity().orElse(null);
        if (activity != activity1) {
            this.getSoundForCurrentActivity().ifPresent(this.mob::playSound);
        }

        this.mob.setAggressive(this.dynamicBrain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
        if (!this.dynamicBrain.hasMemoryValue(MemoryModuleType.RIDE_TARGET) && isPigmyAgeAppropriateRiding(this.mob)) {
            this.mob.stopRiding();
        }

        if (!this.dynamicBrain.hasMemoryValue(MemoryModuleType.CELEBRATE_LOCATION)) {
            this.dynamicBrain.eraseMemory(MemoryModuleType.DANCING);
        }

        this.mob.setDancing(this.dynamicBrain.hasMemoryValue(MemoryModuleType.DANCING));
    }

    @Override
    public SoundEvent getSoundForActivity(Activity activity) {
        if (activity == Activity.FIGHT) {
            return SoundEvents.PIGLIN_ANGRY;
        } else if (this.mob.isConverting()) {
            return SoundEvents.PIGLIN_RETREAT;
        } else if (activity == Activity.AVOID && isNearAvoidTarget(this.mob)) {
            return SoundEvents.PIGLIN_RETREAT;
        } else if (activity == Activity.ADMIRE_ITEM) {
            return SoundEvents.PIGLIN_ADMIRING_ITEM;
        } else if (activity == Activity.CELEBRATE) {
            return SoundEvents.PIGLIN_CELEBRATE;
        } else if (seesPlayerHoldingWantedItem(this.mob)) {
            return SoundEvents.PIGLIN_JEALOUS;
        } else {
            return isNearRepellent(this.mob) ? SoundEvents.PIGLIN_RETREAT : SoundEvents.PIGLIN_AMBIENT;
        }
    }

    @Override
    public void wasHurtBy(LivingEntity attacker) {
        if (!(attacker instanceof PigmyEntity)) {
            if (isHoldingItemInOffHand(this.mob)) {
                pigmyStopHoldingOffhandItem(this.mob, false);
            }

            Brain<PigmyEntity> brain = this.mob.getBrain();
            brain.eraseMemory(MemoryModuleType.CELEBRATE_LOCATION);
            brain.eraseMemory(MemoryModuleType.DANCING);
            brain.eraseMemory(MemoryModuleType.ADMIRING_ITEM);
            if (attacker instanceof PlayerEntity) {
                brain.setMemoryWithExpiry(MemoryModuleType.ADMIRING_DISABLED, true, 400L);
            }

            getAvoidTarget(this.mob).ifPresent((p_234462_2_) -> {
                if (p_234462_2_.getType() != attacker.getType()) {
                    brain.eraseMemory(MemoryModuleType.AVOID_TARGET);
                }

            });
            if (this.mob.isBaby()) {
                brain.setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, attacker, 100L);
                if (isAttackAllowed(attacker)) {
                    broadcastAngerTarget(this.mob, attacker);
                }

            } else if (attacker instanceof HoglinEntity && hoglinsOutnumberPiglins(this.mob)) {
                setAvoidTargetAndDontHuntForAWhile(this.mob, attacker);
                broadcastRetreatToPigmies(this.mob, attacker);
            } else {
                maybeRetaliate(this.mob, attacker);
            }
        }
    }

    @Override
    protected List<Pair<Task<? super T>, Integer>> getIdleLookBehaviors(){
        float maxDist = 8.0F;
        return Arrays.asList(
                Pair.of(new LookAtEntityTask(EntityType.PLAYER, maxDist), 1),
                Pair.of(new LookAtEntityTask(EntityType.PIGLIN, maxDist), 1),
                Pair.of(new LookAtEntityTask(maxDist), 1),
                Pair.of(new DummyTask(30, 60), 1)
        );
    }

    @Override
    protected List<Pair<Task<? super T>, Integer>> getIdleMovementBehaviors(){
        float speedModifer = 0.6F;
        return Arrays.asList(
                Pair.of(new WalkRandomlyTask(speedModifer), 2),
                Pair.of(InteractWithEntityTask.of(EntityType.PIGLIN, 8, MemoryModuleType.INTERACTION_TARGET, speedModifer, 2), 2),
                Pair.of(new SupplementedTask<>(
                        TaskManager::doesntSeeAnyPlayerHoldingWantedItem,
                        new WalkTowardsLookTargetTask(speedModifer, 3)), 2),
                Pair.of(new DummyTask(30, 60), 1)
        );
    }

    @Override
    protected List<Task<? super T>> getCoreTasks() {
        List<Task<? super T>> coreTasks = super.getCoreTasks();
        coreTasks.add(
                new LookTask(45, 90));
        coreTasks.add(
                new WalkToTargetTask());
        coreTasks.add(
                new InteractWithDoorTask());
        coreTasks.add(babyAvoidNemesis());
        coreTasks.add(avoidZombified());
        coreTasks.add(new FinishAdmiringItemTask<>(PigmyTaskManager::performPigmyBarter, TaskManager::canFinishAdmiringOffhandItem));
        coreTasks.add(new AdmiringItemTask<>(120, PiglinTaskManager::isPiglinLoved));
        coreTasks.add(new EndAttackTask(300, PiglinTaskManager::wantsToDance));
        coreTasks.add(
                new GetAngryTask<>());
        return coreTasks;
    }

    @Override
    protected List<Task<? super T>> getIdleTasks() {
        List<Task<? super T>> idleTasks = super.getIdleTasks();
        idleTasks.add(new LookAtEntityTask(PiglinTaskManager::isPlayerHoldingLovedItem, 14.0F));
        idleTasks.add(
                new ForgetAttackTargetTask<>(
                        AbstractPiglinEntity::isAdult,
                        TaskManager::findNearestValidAttackTargetFor
                ));

        // TODO: What do Pigmies hunt?
        /*
        idleTasks.add(
                new SupplementedTask<>(PiglinReflectionHelper::reflectCanHunt, new PiglinsHuntHoglinsTask<>())
        );
         */
        idleTasks.add(
                avoidRepellent()
        );
        idleTasks.add(
                babySometimesRideBabyHoglin()
        );
        idleTasks.add(
                adultSometimesRideAdultHoglin()
        );
        idleTasks.add(
                createIdleLookBehaviors()
        );
        idleTasks.add(
                createIdleMovementBehaviors()
        );
        idleTasks.add(
                new FindInteractionAndLookTargetTask(EntityType.PLAYER, 4)
        );
        return idleTasks;
    }

    @Override
    protected List<Task<? super T>> getFightTasks() {
        List<Task<? super T>> fightTasks = super.getFightTasks();
        fightTasks.add(
                new FindNewAttackTargetTask<>((potentialAttackTarget) -> !isNearestValidAttackTarget(potentialAttackTarget))
        );
        fightTasks.add(
                new SupplementedTask<>(TaskManager::hasCrossbow, new AttackStrafingTask<>(5, 0.75F))
        );
        fightTasks.add(
                new MoveToTargetTask(1.0F)
        );
        fightTasks.add(
                new AttackTargetTask(20)
        );
        fightTasks.add(new ShootTargetTask<>());
        // TODO: What do Pigmies hunt?
        //fightTasks.add(new FinishHuntingTask<>(EntityType.HOGLIN, PiglinTaskManager::dontKillAnyMoreHoglinsForAWhile));
        fightTasks.add(new PredicateTask<>(PiglinTaskManager::isNearZombified, MemoryModuleType.ATTACK_TARGET));
        return fightTasks;
    }

    @Override
    protected List<Task<? super T>> getAdmireItemTasks() {
        List<Task<? super T>> admireItemTasks = super.getAdmireItemTasks();
        admireItemTasks.add(
                new PickupWantedItemTask<>(PiglinTaskManager::isNotHoldingLovedItemInOffHand, 1.0F, true, 9)
        );
        admireItemTasks.add(
                new ForgetAdmiringItemTask<>(9)
        );
        admireItemTasks.add(
                new IgnoreAdmiringItemTask<>(200, 200, TaskManager::isNotHoldingItemInOffhand)
        );
        return admireItemTasks;
    }

    @Override
    protected List<Task<? super T>> getCelebrateTasks() {
        List<Task<? super T>> celebrateTasks = super.getCelebrateTasks();
        celebrateTasks.add(
                avoidRepellent()
        );
        celebrateTasks.add(
                new ForgetAttackTargetTask<PigmyEntity>(AbstractPiglinEntity::isAdult, TaskManager::findNearestValidAttackTargetFor)
                );
        celebrateTasks.add(
                new ForgetAttackTargetTask<PigmyEntity>(AbstractPiglinEntity::isAdult, TaskManager::findNearestValidAttackTargetFor)
                );

        celebrateTasks.add(
                new SupplementedTask<PigmyEntity>((pigmy) -> !pigmy.isDancing(), new HuntCelebrationTask<>(2, 1.0F))
                );
        celebrateTasks.add(
                new SupplementedTask<PigmyEntity>(PigmyEntity::isDancing, new HuntCelebrationTask<>(4, 0.6F))
                );
        celebrateTasks.add(
                new FirstShuffledTask<>(
                        ImmutableList.of(
                                Pair.of(new LookAtEntityTask(EntityType.PIGLIN, 8.0F), 1),
                                Pair.of(new WalkRandomlyTask(0.6F, 2, 1), 1),
                                Pair.of(new DummyTask(10, 20), 1)))
        );
        return celebrateTasks;
    }

    @Override
    protected List<Task<? super T>> getAvoidTasks() {
        List<Task<? super T>> avoidTasks = super.getAvoidTasks();
        avoidTasks.add(
                RunAwayTask.entity(MemoryModuleType.AVOID_TARGET, 1.0F, 12, true)
        );
        avoidTasks.add(
                createIdleLookBehaviors()
                );
        avoidTasks.add(
                createIdleMovementBehaviors()
                );
        avoidTasks.add(
                new PredicateTask<PigmyEntity>(PiglinTaskManager::wantsToStopFleeing, MemoryModuleType.AVOID_TARGET)
        );
        return avoidTasks;
    }

    @Override
    protected List<Task<? super T>> getRideTasks() {
        List<Task<? super T>> rideTasks = super.getRideTasks();
        rideTasks.add(
                new RideEntityTask<>(0.8F)
                );
        rideTasks.add(
                new LookAtEntityTask(PiglinTaskManager::isPlayerHoldingLovedItem, 8.0F)
                );
        rideTasks.add(
                new SupplementedTask<>(Entity::isPassenger, createIdleLookBehaviors())
                );
        rideTasks.add(
                new StopRidingEntityTask<>(8, PigmyTaskManager::pigmyWantsToStopRiding)
        );
        return rideTasks;
    }

    // STATIC TASKS

    protected static RunSometimesTask<AbstractPiglinEntity> adultSometimesRideAdultHoglin() {
        return new RunSometimesTask<>(new PiglinIdleActivityTask<>(AbstractPiglinEntity::isAdult, MemoryModuleTypeRegistry.NEAREST_VISIBLE_ADULT_HOGLIN.get(), MemoryModuleType.RIDE_TARGET, RIDE_DURATION), RIDE_START_INTERVAL);
    }

    // STATIC HELPER METHODS

    public static void broadcastRetreatToPigmies(PigmyEntity pigmy, LivingEntity targetIn) {
        getVisibleAdultPiglins(pigmy)
                .stream()
                .filter(
                        (visibleAdultPiglin) -> visibleAdultPiglin instanceof PigmyEntity)
                .forEach(
                        (pygmy) -> retreatFromNearestTarget(pygmy, targetIn));
    }

    public static boolean isPigmyAgeAppropriateRiding(PigmyEntity pigmy) {
        Entity vehicle = pigmy.getVehicle();
        boolean ridingBabyPigmy = vehicle instanceof PigmyEntity && ((PigmyEntity) vehicle).isBaby();
        boolean ridingBabyHoglin = vehicle instanceof HoglinEntity && ((HoglinEntity) vehicle).isBaby();

        boolean isBaby = pigmy.isBaby();
        if(isBaby){
            return ridingBabyPigmy || ridingBabyHoglin;
        } else{
            return vehicle instanceof HoglinEntity && ((HoglinEntity)vehicle).isAdult();
        }
    }
    public static void pigmyStopHoldingOffhandItem(PigmyEntity pigmy, boolean doBarter) {
        ItemStack itemstack = pigmy.getItemInHand(Hand.OFF_HAND);
        pigmy.setItemInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        if (pigmy.isAdult()) {
            boolean isPiglinBarter = isBarteringItem(itemstack);
            if (doBarter && isPiglinBarter) {
                throwItems(pigmy, getPigmyBarterResponseItems(pigmy));
            } else if (!isPiglinBarter) {
                boolean didEquip = pigmy.equipItemIfPossible(itemstack);
                if (!didEquip) {
                    putInInventory(pigmy, itemstack);
                }
            }
        } else {
            boolean didEquip = pigmy.equipItemIfPossible(itemstack);
            if (!didEquip) {
                ItemStack mainHandItem = pigmy.getMainHandItem();
                if (isLovedItem(mainHandItem.getItem())) {
                    putInInventory(pigmy, mainHandItem);
                } else {
                    throwItems(pigmy, Collections.singletonList(mainHandItem));
                }

                pigmy.holdInMainHand(itemstack);
            }
        }
    }

    public static <T extends PigmyEntity> Void performPigmyBarter(T piglin) {
        pigmyStopHoldingOffhandItem(piglin, true);
        return null;
    }

    public static List<ItemStack> getPigmyBarterResponseItems(PigmyEntity pigmy) {
        MinecraftServer server = pigmy.level.getServer();
        if (server != null) {
            LootTable loottable = server.getLootTables().get(GreedAndBleedLootTables.PIGMY_BARTERING);
            return loottable.getRandomItems((new LootContext.Builder((ServerWorld) pigmy.level)).withParameter(LootParameters.THIS_ENTITY, pigmy).withRandom(pigmy.level.random).create(LootParameterSets.PIGLIN_BARTER));
        }

        return new ArrayList<>();
    }

    public static boolean pigmyWantsToStopRiding(PigmyEntity pigmy, Entity vehicle) {
        if (!(vehicle instanceof MobEntity)) {
            return false;
        } else {
            boolean isBaby = pigmy.isBaby();
            MobEntity mobVehicle = (MobEntity)vehicle;
            boolean ridingWalkingPigmy = mobVehicle instanceof PigmyEntity
                    && mobVehicle.getVehicle() == null;
            if(isBaby){
                return !mobVehicle.isBaby()
                        || !mobVehicle.isAlive()
                        || wasHurtRecently(pigmy)
                        || wasHurtRecently(mobVehicle)
                        || ridingWalkingPigmy;
            } else{
                return mobVehicle.isBaby()
                        || !mobVehicle.isAlive()
                        || wasHurtRecently(pigmy)
                        || wasHurtRecently(mobVehicle)
                        || ridingWalkingPigmy;
            }
        }
    }

}
