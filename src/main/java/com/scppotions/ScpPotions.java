package com.scppotions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.logging.LogUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/** Optional SCP effect and brewing integration. */
@Mod(ScpPotions.MOD_ID)
public final class ScpPotions {
    public static final String MOD_ID = "scp_potions";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENCYCLOPEDIA = "encyclopedia_of_common_diseases";
    private static final String FLESH = "flesh_that_hates";
    private static final String OLD_MAN = "theoldman";
    private static final String SNAIL = "corrosive_snail";
    private static final String ELECTRIC_SHEEP = "electric_sheep";
    private static final String OLD_AI = "the_old_ai";
    private static final ResourceLocation BOOK_ID =
        new ResourceLocation(ENCYCLOPEDIA, "encyclopedia_of_common_diseases");
    private static final ResourceLocation NATIVE_FLESH_POTION_ID =
        new ResourceLocation(FLESH, "flesh_infection_potion");
    private static final int COMMON_DISEASE_COLOR = 0x795390;
    private static final int LONG_DURATION_TICKS = 20 * 180;
    private static final int FLESH_DURATION_TICKS = 20 * 60 * 60;
    private static final int SLOW_ARROW_TICKS = 20 * 20;
    private static final int LONG_SLOW_ARROW_TICKS = 20 * 30;
    private static final int SLOW_CLOUD_TICKS = 20 * 25;
    private static final int LONG_SLOW_CLOUD_TICKS = 20 * 50;
    // Progressive infections need longer arrow and cloud durations.
    private static final int INFECTION_ARROW_TICKS = 20 * 60;
    private static final int LONG_INFECTION_ARROW_TICKS = 20 * 90;
    private static final int INFECTION_CLOUD_TICKS = 20 * 75;
    private static final int LONG_INFECTION_CLOUD_TICKS = 20 * 100;
    // A temporary marker selects one Rage target without spawning SCP-096.
    private static final int LONG_LIVED_MARKER_TICKS = 1_000_000_000;
    private static final int RAGE_SELECTION_TICKS = 20 * 15;
    private static final int SHY_GUY_GUARD_RADIUS = 128;
    private static final int ERIC_EFFECT_TICKS = 20 * 10;
    private static final int ERIC_MOB_EFFECT_TICKS = 20 * 10;
    private static final int CORRUPTION_TICKS = 20 * 12;
    private static final int LONG_CORRUPTION_TICKS = 20 * 30;
    private static final int SCP012_BLEEDING_TICKS = 20 * 20;
    private static final int LONG_SCP012_BLEEDING_TICKS = 20 * 40;
    // SCP-420-J applies secondary effects on start, not over time.
    private static final int SCP420J_HIGH_TICKS = 20 * 9;
    // Keep attribution beyond the final delayed SCP-420-J message.
    private static final int SCP420J_DIALOGUE_TICKS = 20 * 12;
    private static final String HIGH_DIALOGUE_UNTIL = MOD_ID + ":high_dialogue_until";
    private static final String HIGH_TELLRAW_PREFIX =
        "tellraw @a[distance=..10] [\"<\",{\"selector\":\"@p\"},\"> ";
    // Keep Melting short; progression remains controlled by its source mod.
    private static final int MELTING_TICKS = 20 * 30;
    private static final int LONG_MELTING_TICKS = 20 * 60;
    private static final String ERIC_LAST_SOUND = MOD_ID + ":eric_last_sound";
    private static final String VERMIN_ORIGIN = MOD_ID + ":potion_vermin";
    private static final String VERMIN_OWNER = MOD_ID + ":vermin_owner";
    private static final int VERMIN_PER_VICTIM_CAP = 12;
    private static final int VERMIN_LOCAL_CAP = 40;
    private static final Set<String> VERMIN_ENTITY_NAMES = Set.of(
        "cockroach", "maggot", "worm", "centipede", "spider", "rat");

    private static final DeferredRegister<MobEffect> MARKER_EFFECTS =
        DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MOD_ID);
    private static final RegistryObject<MobEffect> RAGE_POTION_MARKER =
        MARKER_EFFECTS.register("rage_potion_marker", RageSelectionMarker::new);
    // Preserve the legacy registry ID for existing saves.
    private static final RegistryObject<MobEffect> PENDING_RAGE =
        MARKER_EFFECTS.register("pending_rage", PendingRageEffect::new);
    private static final RegistryObject<MobEffect> VERMIN_POTION_MARKER =
        MARKER_EFFECTS.register("vermin_potion_marker", VerminPotionMarker::new);
    // Play SCP-066 music directly, avoiding the native Deafness effect.
    private static final RegistryObject<MobEffect> ERIC_POTION_MARKER =
        MARKER_EFFECTS.register("eric_potion_marker", AcousticTraumaMarker::new);
    // Use targeted bleeding damage instead of SCP-012's player-only procedure.
    private static final RegistryObject<MobEffect> SCP012_BLEEDING =
        MARKER_EFFECTS.register("scp012_bleeding", Scp012BleedingEffect::new);
    // Hidden marker associates High dialogue with the affected mob.
    private static final RegistryObject<MobEffect> HIGH_DIALOGUE_MARKER =
        MARKER_EFFECTS.register("high_dialogue_marker", HighDialogueMarker::new);

    private static final DeferredRegister<Item> SPECIMEN_ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    private static final DeferredRegister<Potion> POTIONS =
        DeferredRegister.create(ForgeRegistries.POTIONS, MOD_ID);

    // Harvestable entity IDs; extraction has no cooldown or limit.
    private static final List<SpecimenSource> SPECIMEN_SOURCES = List.of(
        new SpecimenSource("with_many_voices", "with_many_voices", "amnestic_residue"),
        new SpecimenSource("theoldman", "the_old_man", "corrosive_secretion"),
        new SpecimenSource("theoldman", "the_old_man_captured", "corrosive_secretion"),
        new SpecimenSource("corrosive_snail", "corrosive_snail", "corrosive_secretion"),
        new SpecimenSource("corrosive_snail", "corrosive_snail_dormant", "corrosive_secretion"),
        new SpecimenSource("eyeball", "scp_718", "infected_eye_fluid"),
        new SpecimenSource("eyeball", "scp_718_bubble", "infected_eye_fluid"),
        // Exclude the intangible Boogeyman hallucination.
        new SpecimenSource("theboogeyman", "boogeyman", "nightmare_residue"),
        new SpecimenSource("theboogeyman", "stalking_boogeyman", "nightmare_residue"),
        new SpecimenSource("the_snowman", "scp_4230", "anomalous_snow"),
        new SpecimenSource("the_snowman", "scp_4230_a", "anomalous_snow"),
        new SpecimenSource("the_snowman", "scp_4230_b", "anomalous_snow"),
        new SpecimenSource("bone_hive", "bone_hive", "hive_secretion"),
        new SpecimenSource("bone_hive", "scp_439_warrior", "hive_secretion"),
        new SpecimenSource("bone_hive", "scp_439_queen", "hive_secretion"),
        new SpecimenSource("the_vermin_god", "host", "infested_specimen"),
        new SpecimenSource("the_vermin_god", "cockroach", "infested_specimen"),
        new SpecimenSource("the_vermin_god", "maggot", "infested_specimen"),
        new SpecimenSource("the_vermin_god", "worm", "infested_specimen"),
        new SpecimenSource("the_vermin_god", "centipede", "infested_specimen"),
        new SpecimenSource("the_vermin_god", "spider", "infested_specimen"),
        new SpecimenSource("the_vermin_god", "rat", "infested_specimen"),
        new SpecimenSource("humans_refuted", "humans_refuted", "acidic_secretion"),
        new SpecimenSource("ice_spider", "ice_spider", "frozen_venom")
    );

    // Roll a disease when the potion is applied.
    private static final RegistryObject<Potion> COMMON_DISEASES =
        POTIONS.register("common_diseases", () -> new Potion("scp_potions.common_diseases"));
    // Longer duration, same display name.
    private static final RegistryObject<Potion> LONG_COMMON_DISEASES =
        POTIONS.register("long_common_diseases", () -> new Potion("scp_potions.common_diseases"));

    // Prefer standalone effects; use Thick Potions for their brewing routes.
    static final List<Disease> DISEASES = List.of(
        new Disease("amnesia", "amnesia", Items.PAPER, 20 * 40,
            List.of(new Provider("with_many_voices", "amnesia", "scp_potions:amnestic_residue"))),
        new Disease("corrision", "corrosion", Items.SLIME_BALL, 20 * 30,
            // Share Corrosion; prefer The Old Man when both are installed.
            List.of(new Provider(OLD_MAN, "corrision_effect", "scp_potions:corrosive_secretion"),
                    new Provider(SNAIL, "corrosion_effect", "scp_potions:corrosive_secretion"))),
        new Disease("eye_infection", "eye_infection", Items.SPIDER_EYE, 20 * 30,
            List.of(new Provider("eyeball", "eye_infection", "scp_potions:infected_eye_fluid"))),
        new Disease("fear", "fear", Items.PHANTOM_MEMBRANE, 20 * 40,
            List.of(new Provider("theboogeyman", "fear", "scp_potions:nightmare_residue"))),
        new Disease("dentophobia", "dentophobia", Items.BONE, 20 * 40,
            List.of(new Provider("the_grinner", "dentophobia", "the_grinner:molar_serum"))),
        new Disease("scp_42301", "scp_42301", Items.SNOWBALL, 20 * 40,
            List.of(new Provider("the_snowman", "scp_42301", "scp_potions:anomalous_snow"))),
        new Disease("infection", "infection", Items.POISONOUS_POTATO, 20 * 120,
            List.of()),
        new Disease("scp_008_infection", "scp_008_infection", Items.ROTTEN_FLESH, 20 * 60,
            List.of(new Provider("zombie_plague", "scp_008_infection", "zombie_plague:canister"))),
        new Disease("flesh_infection", "flesh_infection", Items.BEEF, FLESH_DURATION_TICKS,
            List.of(new Provider(FLESH, "flesh_infection", "flesh_that_hates:flesh"))),
        new Disease("scp_439_infection", "scp_439_infection", Items.BONE_MEAL, 20 * 120,
            List.of(new Provider("bone_hive", "scp_439_infection", "scp_potions:hive_secretion"))),
        // Standalone-only effects are not SCP-1025 disease outcomes.
        new Disease(null, "electric_shock", null, 20 * 5,
            List.of(new Provider(ELECTRIC_SHEEP, "electric_shock", "electric_sheep:electric_wool"),
                    new Provider(OLD_AI, "electric_shock", "the_old_ai:scp_079_offline"))),
        new Disease(null, "boiling", null, 20 * 30,
            List.of(new Provider("scarlet_king", "boiling", "scarlet_king:manifestation_clock"))),
        new Disease(null, "call_of_the_hive", null, 20 * 60,
            List.of(new Provider(FLESH, "call_of_the_hive", "flesh_that_hates:flesh"))),
        new Disease(null, "rage", null, LONG_LIVED_MARKER_TICKS,
            List.of(new Provider("shy_guy", "rage", "shy_guy:disturbing_photo"),
                    new Provider("shy_guy", "rage", "shy_guy:scrambled_photo"))),
        new Disease(null, "vermin_infection", null, 20 * 60,
            List.of(new Provider("the_vermin_god", "vermin_god", "scp_potions:infested_specimen"))),
        new Disease(null, "acoustic_trauma", null, ERIC_EFFECT_TICKS,
            List.of(new Provider("erics_toy", "earrape", "erics_toy:cupcake"))),
        new Disease(null, "acid_spit", null, 20 * 25,
            List.of(new Provider("humans_refuted", "acid_spit", "scp_potions:acidic_secretion"))),
        new Disease(null, "frostbite", null, 20 * 12,
            List.of(new Provider("ice_spider", "frostbite", "scp_potions:frozen_venom"))),
        new Disease(null, "corruption", null, CORRUPTION_TICKS,
            List.of(new Provider("malo", "corruption", "malo:phone"))),
        new Disease(null, "bleeding", null, SCP012_BLEEDING_TICKS,
            List.of(new Provider("a_bad_composition", "bleeding", "a_bad_composition:scp_012"))),
        // Use the source mod's craftable SCP-420-J item.
        new Disease(null, "high", null, SCP420J_HIGH_TICKS,
            List.of(new Provider("thebestweedintheworld", "high",
                "thebestweedintheworld:scp_420_j"))),
        // Sludge Meat is a native mob drop.
        new Disease(null, "melting", null, MELTING_TICKS,
            List.of(new Provider("when_day_breaks", "melting",
                "when_day_breaks:sludge_meat")))
    );

    private final Map<Disease, RegistryObject<Potion>> targetedPotions = new LinkedHashMap<>();
    private final Map<Disease, RegistryObject<Potion>> longTargetedPotions = new LinkedHashMap<>();
    private final Map<String, RegistryObject<Item>> specimenItems = new LinkedHashMap<>();
    private final boolean hasEncyclopedia;

    public ScpPotions() {
        hasEncyclopedia = ModList.get().isLoaded(ENCYCLOPEDIA);

        // Register specimens conditionally; Corrosion shares one specimen.
        for (SpecimenSource source : SPECIMEN_SOURCES) {
            if (ModList.get().isLoaded(source.modId()) &&
                !specimenItems.containsKey(source.specimenItem())) {
                specimenItems.put(source.specimenItem(), SPECIMEN_ITEMS.register(
                    source.specimenItem(), () -> new Item(new Item.Properties().stacksTo(64))));
            }
        }
        // Register only installed providers; reuse Flesh's native potion.
        for (Disease disease : activeDiseases()) {
            if (isNativeFlesh(disease)) continue;
            targetedPotions.put(disease, POTIONS.register(disease.potionId(),
                () -> makeTargetedPotion(disease, false)));
            // Match Flesh's native one-hour duration.
            if (!isFlesh(disease) && !isFastElectricShock(disease) &&
                !isRage(disease) && !isEric(disease) && !isHigh(disease)) {
                longTargetedPotions.put(disease, POTIONS.register("long_" + disease.potionId(),
                    () -> makeTargetedPotion(disease, true)));
            }
        }

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MARKER_EFFECTS.register(modBus);
        SPECIMEN_ITEMS.register(modBus);
        POTIONS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addSpecimensToCreativeTab);
        MinecraftForge.EVENT_BUS.addListener(this::onDrinkFinished);
        MinecraftForge.EVENT_BUS.addListener(this::onProjectileImpact);
        MinecraftForge.EVENT_BUS.addListener(this::onEntityJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(this::onMobEffectAdded);
        MinecraftForge.EVENT_BUS.addListener(this::onHighDialogueCommand);
        MinecraftForge.EVENT_BUS.addListener(this::onLivingTick);
        MinecraftForge.EVENT_BUS.addListener(this::onItemTooltip);
        MinecraftForge.EVENT_BUS.addListener(this::onSpecimenCollection);
    }

    /** Expose specimens in Creative Ingredients and search. */
    private void addSpecimensToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) return;
        for (RegistryObject<Item> specimen : specimenItems.values()) {
            event.accept(specimen.get());
        }
    }

    private static Potion makeTargetedPotion(Disease disease, boolean longer) {
        MobEffect effect = getEffect(disease);
        // Missing optional effects leave inert potions without recipes.
        if (effect == null) {
            return new Potion("scp_potions." + disease.potionId());
        }
        if (isRage(disease)) {
            return new Potion("scp_potions.rage",
                new MobEffectInstance(RAGE_POTION_MARKER.get(), RAGE_SELECTION_TICKS,
                    0, false, false, false));
        }
        if (isEric(disease)) {
            return new Potion("scp_potions.acoustic_trauma",
                new MobEffectInstance(ERIC_POTION_MARKER.get(), ERIC_EFFECT_TICKS,
                    0, false, false, false));
        }
        if (isBleeding(disease)) {
            return new Potion("scp_potions.bleeding", new MobEffectInstance(
                SCP012_BLEEDING.get(), baseDurationTicks(disease, longer), 0));
        }
        if (isVermin(disease)) {
            return new Potion("scp_potions.vermin_infection",
                hiddenMarker(VERMIN_POTION_MARKER.get()),
                new MobEffectInstance(effect, baseDurationTicks(disease, longer), 0));
        }
        if (isFrostbite(disease)) {
            int ticks = baseDurationTicks(disease, longer);
            return new Potion("scp_potions.frostbite",
                new MobEffectInstance(effect, ticks, 0),
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1),
                new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 0));
        }
        if (isCorruption(disease)) {
            int ticks = baseDurationTicks(disease, longer);
            return new Potion("scp_potions.corruption",
                new MobEffectInstance(effect, ticks, 0),
                new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0),
                new MobEffectInstance(MobEffects.CONFUSION, Math.min(20 * 6, ticks), 0));
        }
        if (isHigh(disease)) {
            return new Potion("scp_potions.high",
                new MobEffectInstance(HIGH_DIALOGUE_MARKER.get(),
                    SCP420J_DIALOGUE_TICKS, 0, false, false, false),
                new MobEffectInstance(effect, SCP420J_HIGH_TICKS, 0));
        }
        return new Potion("scp_potions." + disease.potionId(),
            new MobEffectInstance(effect, baseDurationTicks(disease, longer), 0));
    }

    private static MobEffectInstance hiddenMarker(MobEffect effect) {
        return new MobEffectInstance(effect, LONG_LIVED_MARKER_TICKS, 0,
            false, false, false);
    }

    static boolean hasEncyclopedia() { return ModList.get().isLoaded(ENCYCLOPEDIA); }

    static boolean isFlesh(Disease disease) { return "flesh_infection".equals(disease.potionId()); }

    static boolean isNativeFlesh(Disease disease) {
        return isFlesh(disease) && ModList.get().isLoaded(FLESH);
    }

    private static boolean isFastElectricShock(Disease disease) {
        return "electric_shock".equals(disease.potionId());
    }

    private static boolean isRage(Disease disease) {
        return "rage".equals(disease.potionId());
    }

    private static boolean isEric(Disease disease) {
        return "acoustic_trauma".equals(disease.potionId());
    }

    private static boolean isVermin(Disease disease) {
        return "vermin_infection".equals(disease.potionId());
    }

    private static boolean isFrostbite(Disease disease) {
        return "frostbite".equals(disease.potionId());
    }

    private static boolean isCorruption(Disease disease) {
        return "corruption".equals(disease.potionId());
    }

    private static boolean isBleeding(Disease disease) {
        return "bleeding".equals(disease.potionId());
    }

    private static boolean isHigh(Disease disease) {
        return "high".equals(disease.potionId());
    }

    private static boolean isMelting(Disease disease) {
        return "melting".equals(disease.potionId());
    }

    static List<Disease> activeDiseases() {
        List<Disease> active = new ArrayList<>();
        for (Disease disease : DISEASES) {
            if (hasAnyProvider(disease)) active.add(disease);
        }
        return active;
    }

    private static boolean hasAnyProvider(Disease disease) {
        if (disease.effectId() != null && hasEncyclopedia()) return true;
        for (Provider provider : disease.providers()) {
            if (ModList.get().isLoaded(provider.modId())) return true;
        }
        return false;
    }

    static List<Provider> standaloneProviders(Disease disease) {
        List<Provider> present = new ArrayList<>();
        Set<String> usedIngredients = new HashSet<>();
        for (Provider provider : disease.providers()) {
            if (!ModList.get().isLoaded(provider.modId())) continue;
            // Deduplicate identical recipes, including shared Corrosion.
            if (!usedIngredients.add(provider.ingredientId())) continue;
            present.add(provider);
        }
        return present;
    }

    static Item ingredientFor(Provider provider) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(provider.ingredientId()));
        return item == null ? Items.AIR : item;
    }

    static MobEffect getEffect(Disease disease) {
        if (isBleeding(disease)) {
            // SCP-012 Bleeding uses targeted server-side damage.
            if (!ModList.get().isLoaded("a_bad_composition")) return null;
            MobEffect nativeBleeding = ForgeRegistries.MOB_EFFECTS.getValue(
                new ResourceLocation("a_bad_composition", "bleeding"));
            return nativeBleeding == null ? null : SCP012_BLEEDING.get();
        }
        for (Provider provider : disease.providers()) {
            if (!ModList.get().isLoaded(provider.modId())) continue;
            MobEffect found = ForgeRegistries.MOB_EFFECTS.getValue(
                new ResourceLocation(provider.modId(), provider.effectId()));
            if (found != null) return found;
        }
        if (hasEncyclopedia() && disease.effectId() != null) {
            return ForgeRegistries.MOB_EFFECTS.getValue(
                new ResourceLocation(ENCYCLOPEDIA, disease.effectId()));
        }
        return null;
    }

    static Potion potionFor(Disease disease, boolean longer) {
        if (isNativeFlesh(disease)) {
            return longer ? null : ForgeRegistries.POTIONS.getValue(NATIVE_FLESH_POTION_ID);
        }
        if ((isFlesh(disease) || isFastElectricShock(disease) ||
             isRage(disease) || isEric(disease) || isHigh(disease)) && longer) return null;
        return ForgeRegistries.POTIONS.getValue(new ResourceLocation(MOD_ID,
            (longer ? "long_" : "") + disease.potionId()));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Register brewing recipes on Forge's setup queue.
        event.enqueueWork(() -> {
            if (hasEncyclopedia) {
                Item book = ForgeRegistries.ITEMS.getValue(BOOK_ID);
                if (book != null && book != Items.AIR) {
                    BrewingRecipeRegistry.addRecipe(
                        new ExactPotionRecipe(Potions.AWKWARD, book, COMMON_DISEASES.get(), true));
                    BrewingRecipeRegistry.addRecipe(
                        new ExactPotionRecipe(COMMON_DISEASES.get(), Items.REDSTONE,
                            LONG_COMMON_DISEASES.get(), true));
                }
            }
            for (Disease disease : activeDiseases()) {
                Potion regular = potionFor(disease, false);
                Potion longer = potionFor(disease, true);
                if (getEffect(disease) == null || regular == null) continue;

                // Encyclopedia and standalone routes share the output potion.
                if (hasEncyclopedia && disease.bookIngredient() != null) {
                    BrewingRecipeRegistry.addRecipe(new ExactPotionRecipe(
                        COMMON_DISEASES.get(), disease.bookIngredient(), regular, false));
                    BrewingRecipeRegistry.addRecipe(new ExactPotionRecipe(
                        LONG_COMMON_DISEASES.get(), disease.bookIngredient(),
                        longer != null ? longer : regular, false));
                }

                // Retain Flesh's native recipe; add other standalone catalysts.
                if (!isNativeFlesh(disease)) {
                    for (Provider provider : standaloneProviders(disease)) {
                        Item ingredient = ingredientFor(provider);
                        if (ingredient != Items.AIR) {
                            BrewingRecipeRegistry.addRecipe(new ExactPotionRecipe(
                                Potions.THICK, ingredient, regular, false));
                        }
                    }
                }
                if (longer != null) {
                    BrewingRecipeRegistry.addRecipe(new ExactPotionRecipe(
                        regular, Items.REDSTONE, longer, false));
                }
            }
        });
    }

    private void onDrinkFinished(LivingEntityUseItemEvent.Finish event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide()) return;

        // Finish#getItem references the consumed ingredient.
        ItemStack consumed = event.getItem();
        if (!consumed.is(Items.POTION)) return;
        Potion potion = PotionUtils.getPotion(consumed);
        if (!hasEncyclopedia) return;
        if (potion == COMMON_DISEASES.get()) applyRandomDisease(living, false, false);
        if (potion == LONG_COMMON_DISEASES.get()) applyRandomDisease(living, true, false);
    }

    /** Sample interaction for right-clicked mobs. */
    private void onSpecimenCollection(PlayerInteractEvent.EntityInteract event) {
        if (tryCollectSpecimen(event, event.getTarget(), "standard")) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /** Harvest one specimen per bottle. */
    private boolean tryCollectSpecimen(PlayerInteractEvent event, Entity target, String interaction) {
        // Ignore the offhand to prevent duplicate drops.
        if (event.getHand() != InteractionHand.MAIN_HAND ||
            !event.getItemStack().is(Items.GLASS_BOTTLE)) return false;
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || player.isSpectator()) return false;
        ResourceLocation targetId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (targetId == null) return false;

        for (SpecimenSource source : SPECIMEN_SOURCES) {
            if (!ModList.get().isLoaded(source.modId()) ||
                !targetId.equals(new ResourceLocation(source.modId(), source.entityPath()))) continue;
            Item specimen = ForgeRegistries.ITEMS.getValue(
                new ResourceLocation(MOD_ID, source.specimenItem()));
            if (specimen == null || specimen == Items.AIR) {
                if (!player.level().isClientSide()) {
                    LOGGER.warn("[SCP Potions/Sampling] Matched {} but specimen {} was missing",
                        targetId, source.specimenItem());
                }
                return false;
            }
            if (!player.level().isClientSide()) {
                giveSample(player, event.getItemStack(), specimen);
                LOGGER.info("[SCP Potions/Sampling] Collected {} from {} via {} interaction",
                    source.specimenItem(), targetId, interaction);
            }
            return true;
        }
        // Log only attempted collection from supported SCP namespaces.
        if (!player.level().isClientSide()) {
            for (SpecimenSource source : SPECIMEN_SOURCES) {
                if (source.modId().equals(targetId.getNamespace())) {
                    LOGGER.info("[SCP Potions/Sampling] No specimen mapping for {} via {} interaction",
                        targetId, interaction);
                    break;
                }
            }
        }
        return false;
    }

    private static void giveSample(Player player, ItemStack emptyBottle, Item sample) {
        if (!player.getAbilities().instabuild) emptyBottle.shrink(1);
        ItemStack filled = new ItemStack(sample);
        if (!player.addItem(filled)) player.drop(filled, false);
    }

    /** Share one disease roll across splash targets. */
    private void onProjectileImpact(ProjectileImpactEvent event) {
        if (!COMMON_DISEASES.isPresent()) return;
        if (event.getProjectile().level().isClientSide()) return;

        if (event.getProjectile() instanceof ThrownPotion thrown) {
            ItemStack stack = thrown.getItem();
            Potion potion = PotionUtils.getPotion(stack);
            if (hasEncyclopedia && stack.is(Items.SPLASH_POTION) &&
                (potion == COMMON_DISEASES.get() || potion == LONG_COMMON_DISEASES.get())) {
                boolean longer = potion == LONG_COMMON_DISEASES.get();
                Disease disease = chooseDisease(thrown.level().getRandom());
                MobEffect effect = disease == null ? null : getEffect(disease);
                if (effect == null) return;
                for (LivingEntity target : thrown.level().getEntitiesOfClass(
                        LivingEntity.class, thrown.getBoundingBox().inflate(4.0, 2.0, 4.0),
                        LivingEntity::isAlive)) {
                    double distanceSq = thrown.distanceToSqr(target);
                    if (distanceSq >= 16.0) continue;
                    double strength = 1.0 - Math.sqrt(distanceSq) / 4.0;
                    if (event.getRayTraceResult() instanceof EntityHitResult hit &&
                        hit.getEntity() == target) strength = 1.0;
                    int ticks = (int) (baseDurationTicks(disease, longer) * strength);
                    if (ticks >= 20) target.addEffect(new MobEffectInstance(effect, ticks, 0));
                }
            }
            return;
        }

        if (event.getProjectile() instanceof Arrow arrow &&
            event.getRayTraceResult() instanceof EntityHitResult hit &&
            hit.getEntity() instanceof LivingEntity target) {
            // Read the 1.20.1 arrow's potion ID from entity save data.
            CompoundTag saved = new CompoundTag();
            arrow.saveWithoutId(saved);
            String potionId = saved.getString("Potion");
            // Record arrow targets before High's immediate first message.
            if ((MOD_ID + ":high").equals(potionId) &&
                ModList.get().isLoaded("thebestweedintheworld")) {
                markHighDialogueTarget(target);
            }
            if ((MOD_ID + ":common_diseases").equals(potionId)) {
                applyRandomDisease(target, false, true);
            } else if ((MOD_ID + ":long_common_diseases").equals(potionId)) {
                applyRandomDisease(target, true, true);
            } else {
                // Extend progressive effects beyond vanilla arrow scaling.
                for (Disease disease : activeDiseases()) {
                    // Rage is assigned only through the single-target gate.
                    if (isRage(disease) || isEric(disease)) continue;
                    if (!isSlowActing(disease) && !isFastElectricShock(disease)) continue;
                    boolean longer = (MOD_ID + ":long_" + disease.potionId()).equals(potionId);
                    if (!longer && !(MOD_ID + ":" + disease.potionId()).equals(potionId)) continue;
                    MobEffect effect = getEffect(disease);
                    if (effect != null) {
                        int duration = arrowDurationTicks(disease, longer);
                        target.addEffect(new MobEffectInstance(effect, duration, 0));
                        for (MobEffectInstance extra : supportingEffects(disease, duration)) {
                            target.addEffect(extra);
                        }
                    }
                    break;
                }
            }
        }
    }

    /** Apply SCP-1025 effects to lingering clouds. */
    private void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!COMMON_DISEASES.isPresent()) return;
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel() instanceof ServerLevel server &&
            limitPotionVermin(event, server)) return;
        if (!(event.getEntity() instanceof AreaEffectCloud cloud)) return;

        Potion potion = cloud.getPotion();
        boolean regularCommon = potion == COMMON_DISEASES.get();
        boolean longCommon = potion == LONG_COMMON_DISEASES.get();
        Disease targeted = null;
        boolean targetedLong = false;
        if (!regularCommon && !longCommon) {
            for (Disease disease : activeDiseases()) {
                // Rage clouds use the single-target gate.
                if (isRage(disease) || isEric(disease)) continue;
                if (!isSlowActing(disease) && !isFastElectricShock(disease)) continue;
                Potion regular = potionFor(disease, false);
                Potion longer = potionFor(disease, true);
                if (regular != null && potion == regular) {
                    targeted = disease;
                    break;
                }
                if (longer != null && potion == longer) {
                    targeted = disease;
                    targetedLong = true;
                    break;
                }
            }
            if (targeted == null) return;
        }
        if ((regularCommon || longCommon) && !hasEncyclopedia) return;

        // Persist the disease roll across chunk reloads.
        CompoundTag persistent = cloud.getPersistentData();
        if (persistent.getBoolean(MOD_ID + ":cloud_initialized")) return;
        persistent.putBoolean(MOD_ID + ":cloud_initialized", true);
        Disease disease = targeted != null ? targeted : chooseDisease(cloud.level().getRandom());
        MobEffect effect = disease == null ? null : getEffect(disease);
        if (effect != null) {
            boolean longer = longCommon || targetedLong;
            int duration = lingeringDurationTicks(disease, longer);
            cloud.addEffect(new MobEffectInstance(effect, duration, 0));
            for (MobEffectInstance extra : supportingEffects(disease, duration)) {
                cloud.addEffect(extra);
            }
        }
    }

    /** Match supporting effects to arrow and cloud durations. */
    private static List<MobEffectInstance> supportingEffects(Disease disease, int ticks) {
        if (isFrostbite(disease)) return List.of(
            new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1),
            new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 0));
        if (isCorruption(disease)) return List.of(
            new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0),
            new MobEffectInstance(MobEffects.CONFUSION, Math.min(20 * 6, ticks), 0));
        return List.of();
    }

    private static Disease chooseDisease(RandomSource random) {
        List<Disease> eligible = new ArrayList<>();
        for (Disease disease : DISEASES) {
            // Exclude standalone-only effects from SCP-1025 rolls.
            if (disease.effectId() != null && getEffect(disease) != null) eligible.add(disease);
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(random.nextInt(eligible.size()));
    }

    private static int baseDurationTicks(Disease disease, boolean longer) {
        // Old AI's faster Electric Shock needs a shorter duration.
        if (isFastElectricShock(disease) && !ModList.get().isLoaded(ELECTRIC_SHEEP)) {
            return 20;
        }
        if (isCorruption(disease)) return longer ? LONG_CORRUPTION_TICKS : CORRUPTION_TICKS;
        if (isBleeding(disease)) return longer ? LONG_SCP012_BLEEDING_TICKS : SCP012_BLEEDING_TICKS;
        if (isMelting(disease)) return longer ? LONG_MELTING_TICKS : MELTING_TICKS;
        return longer ? Math.max(LONG_DURATION_TICKS, disease.durationTicks()) : disease.durationTicks();
    }

    private static boolean isSlowActing(Disease disease) {
        return !"amnesia".equals(disease.potionId()) &&
            !"corrosion".equals(disease.potionId()) &&
            !"electric_shock".equals(disease.potionId());
    }

    private static boolean isProgressiveInfection(Disease disease) {
        return switch (disease.potionId()) {
            case "infection", "scp_008_infection", "flesh_infection", "scp_439_infection",
                 "vermin_infection" -> true;
            default -> false;
        };
    }

    private static int arrowDurationTicks(Disease disease, boolean longer) {
        // Keep arrow shocks brief but effective.
        if (isFastElectricShock(disease)) {
            return ModList.get().isLoaded(ELECTRIC_SHEEP) ? 20 * 2 : 12;
        }
        int vanillaTicks = Math.max(1, baseDurationTicks(disease, longer) / 8);
        if (!isSlowActing(disease)) return vanillaTicks;
        if (isProgressiveInfection(disease)) {
            return Math.max(vanillaTicks, longer ? LONG_INFECTION_ARROW_TICKS : INFECTION_ARROW_TICKS);
        }
        return Math.max(vanillaTicks, longer ? LONG_SLOW_ARROW_TICKS : SLOW_ARROW_TICKS);
    }

    private static int lingeringDurationTicks(Disease disease, boolean longer) {
        if (isFastElectricShock(disease)) {
            return ModList.get().isLoaded(ELECTRIC_SHEEP) ? 20 * 2 : 12;
        }
        int vanillaTicks = Math.max(1, baseDurationTicks(disease, longer) / 4);
        if (!isSlowActing(disease)) return vanillaTicks;
        if (isProgressiveInfection(disease)) {
            return Math.max(vanillaTicks, longer ? LONG_INFECTION_CLOUD_TICKS : INFECTION_CLOUD_TICKS);
        }
        return Math.max(vanillaTicks, longer ? LONG_SLOW_CLOUD_TICKS : SLOW_CLOUD_TICKS);
    }

    private static void applyRandomDisease(LivingEntity living, boolean longer, boolean arrow) {
        Disease chosen = chooseDisease(living.getRandom());
        if (chosen == null) return;
        MobEffect effect = getEffect(chosen);
        if (effect != null) {
            living.addEffect(new MobEffectInstance(effect,
                arrow ? arrowDurationTicks(chosen, longer) : baseDurationTicks(chosen, longer), 0));
        }
    }

    private void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) ||
              stack.is(Items.LINGERING_POTION) || stack.is(Items.TIPPED_ARROW))) return;
        Potion potion = PotionUtils.getPotion(stack);
        if (ModList.get().isLoaded("shy_guy") &&
            potion == ForgeRegistries.POTIONS.getValue(new ResourceLocation(MOD_ID, "rage"))) {
            event.getToolTip().add(Component.translatable("tooltip.scp_potions.rage_existing"));
        }
        if (ModList.get().isLoaded("erics_toy") &&
            potion == ForgeRegistries.POTIONS.getValue(new ResourceLocation(MOD_ID, "acoustic_trauma"))) {
            event.getToolTip().add(Component.translatable("tooltip.scp_potions.acoustic_trauma"));
        }
        if (ModList.get().isLoaded("thebestweedintheworld") &&
            potion == ForgeRegistries.POTIONS.getValue(new ResourceLocation(MOD_ID, "high"))) {
            // Hide High's internal dialogue marker from tooltips.
            event.getToolTip().removeIf(line ->
                line.getContents() instanceof TranslatableContents translated &&
                "effect.scp_potions.high_dialogue_marker".equals(translated.getKey()));
        }
        if (!hasEncyclopedia || !COMMON_DISEASES.isPresent()) return;
        if (potion == COMMON_DISEASES.get() || potion == LONG_COMMON_DISEASES.get()) {
            event.getToolTip().add(Component.translatable("tooltip.scp_potions.common_diseases"));
        }
    }

    /** Process potion-only effect markers. */
    private void onMobEffectAdded(MobEffectEvent.Added event) {
        LivingEntity living = event.getEntity();
        if (!living.level().isClientSide() && HIGH_DIALOGUE_MARKER.isPresent() &&
            event.getEffectInstance().getEffect() == HIGH_DIALOGUE_MARKER.get()) {
            // Dialogue attribution outlives short cloud and arrow effects.
            markHighDialogueTarget(living);
        }
        if (living.level().isClientSide() || !RAGE_POTION_MARKER.isPresent() ||
            event.getEffectInstance().getEffect() != RAGE_POTION_MARKER.get() ||
            event.getOldEffectInstance() != null) return;
        LOGGER.info("[SCP Potions/Rage] Marked candidate {} in {}",
            living.getUUID(), living.level().dimension().location());
    }

    /** Track High targets from arrows and other potion forms. */
    private static void markHighDialogueTarget(LivingEntity living) {
        CompoundTag data = living.getPersistentData();
        long deadline = living.level().getGameTime() + SCP420J_DIALOGUE_TICKS;
        data.putLong(HIGH_DIALOGUE_UNTIL,
            Math.max(deadline, data.getLong(HIGH_DIALOGUE_UNTIL)));
    }

    /** Attribute SCP-420-J dialogue to a mob affected by Potion of High. */
    private void onHighDialogueCommand(CommandEvent event) {
        if (!ModList.get().isLoaded("thebestweedintheworld")) return;
        var parsed = event.getParseResults();
        var source = parsed.getContext().getSource();
        Entity speaker = source.getEntity();
        if (!(speaker instanceof LivingEntity living) || speaker instanceof Player ||
            living.level().isClientSide() ||
            living.level().getGameTime() >=
                living.getPersistentData().getLong(HIGH_DIALOGUE_UNTIL)) return;

        String raw = parsed.getReader().getString();
        String command = raw.startsWith("/") ? raw.substring(1) : raw;
        if (!command.startsWith(HIGH_TELLRAW_PREFIX) ||
            !(command.contains("Hey duuuude...") || command.contains("Hold on...") ||
              command.contains("Oh my god, man,") || command.contains("Brooo I guess"))) return;

        // Resolve @s to the affected mob instead of the nearby player.
        String rewritten = command.replace("\"selector\":\"@p\"", "\"selector\":\"@s\"");
        event.setParseResults(source.getServer().getCommands().getDispatcher()
            .parse(rewritten, source));
    }

    private void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide()) return;
        // Consume legacy queue markers while retaining their registry ID.
        if (PENDING_RAGE.isPresent() && living.hasEffect(PENDING_RAGE.get())) {
            living.removeEffect(PENDING_RAGE.get());
        }

        // Consume markers on the next tick; only one Rage target is chosen.
        if (RAGE_POTION_MARKER.isPresent() && living.hasEffect(RAGE_POTION_MARKER.get())) {
            living.removeEffect(RAGE_POTION_MARKER.get());
            tryApplySingleRage(living);
        }

        // Play SCP-066 music without Deafness; rate-limit cloud playback.
        if (ERIC_POTION_MARKER.isPresent() && living.hasEffect(ERIC_POTION_MARKER.get())) {
            MobEffectInstance marker = living.getEffect(ERIC_POTION_MARKER.get());
            int ticks = marker == null ? ERIC_EFFECT_TICKS : Math.max(20, marker.getDuration());
            living.removeEffect(ERIC_POTION_MARKER.get());
            if (!isNativeEntity(living, "erics_toy")) {
                CompoundTag data = living.getPersistentData();
                long now = living.level().getGameTime();
                if (!data.contains(ERIC_LAST_SOUND) || now >= data.getLong(ERIC_LAST_SOUND)) {
                    // One sound cue per splash group.
                    boolean nearbyMusic = !living.level().getEntitiesOfClass(
                        LivingEntity.class, living.getBoundingBox().inflate(8.0),
                        other -> other != living && other.getPersistentData()
                            .getLong(ERIC_LAST_SOUND) > now).isEmpty();
                    if (!nearbyMusic) {
                        SoundEvent music = ForgeRegistries.SOUND_EVENTS.getValue(
                            new ResourceLocation("erics_toy", "entity.erics_toy.music"));
                        if (music != null) {
                            living.level().playSound(null, living.blockPosition(), music,
                                SoundSource.HOSTILE, 0.9F, 1.0F);
                        }
                    }
                    data.putLong(ERIC_LAST_SOUND, now + ERIC_EFFECT_TICKS);
                }
                if (living instanceof Player) {
                    living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, 0));
                } else {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        ERIC_MOB_EFFECT_TICKS, 1));
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                        ERIC_MOB_EFFECT_TICKS, 0));
                }
            }
        }

        // Exclude SCP-027 creatures; leave native infections unchanged.
        if (VERMIN_POTION_MARKER.isPresent() &&
            living.hasEffect(VERMIN_POTION_MARKER.get()) &&
            isNativeEntity(living, "the_vermin_god")) {
            MobEffect infection = ForgeRegistries.MOB_EFFECTS.getValue(
                new ResourceLocation("the_vermin_god", "vermin_god"));
            if (infection != null) living.removeEffect(infection);
            living.removeEffect(VERMIN_POTION_MARKER.get());
        }
    }

    /** Mark one Rage target for an existing SCP-096. */
    private static void tryApplySingleRage(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel server) || !victim.isAlive() ||
            !ModList.get().isLoaded("shy_guy")) return;
        if (isNativeEntity(victim, "shy_guy")) {
            LOGGER.info("[SCP Potions/Rage] Rejected SCP-096 self-target {}", victim.getUUID());
            return;
        }
        MobEffect originalRage = ForgeRegistries.MOB_EFFECTS.getValue(
            new ResourceLocation("shy_guy", "rage"));
        if (originalRage == null) return;
        if (victim.hasEffect(originalRage)) {
            LOGGER.info("[SCP Potions/Rage] Target {} already carries original Rage",
                victim.getUUID());
            return;
        }

        double radiusSq = SHY_GUY_GUARD_RADIUS * SHY_GUY_GUARD_RADIUS;
        AABB area = victim.getBoundingBox().inflate(SHY_GUY_GUARD_RADIUS);

        // Avoid additional Rage targets near an existing marked entity.
        if (!server.getEntitiesOfClass(LivingEntity.class, area,
                other -> other != victim && other.isAlive() &&
                    other.hasEffect(originalRage) && other.distanceToSqr(victim) < radiusSq)
                .isEmpty()) {
            LOGGER.info("[SCP Potions/Rage] Skipped candidate {}: nearby Rage bearer",
                victim.getUUID());
            return;
        }

        // Do not alter SCP-096; retain the original Rage duration.
        boolean applied = victim.addEffect(new MobEffectInstance(originalRage,
            LONG_LIVED_MARKER_TICKS, 0, false, false));
        if (!applied && !victim.hasEffect(originalRage)) {
            LOGGER.warn("[SCP Potions/Rage] Candidate {} rejected original Rage",
                victim.getUUID());
            return;
        }
        LOGGER.info("[SCP Potions/Rage] Marked victim {} with original Shy Guy Rage in {}; " +
            "no SCP-096 entity summoned", victim.getUUID(), server.dimension().location());
    }

    private static boolean isNativeEntity(Entity entity, String namespace) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && namespace.equals(id.getNamespace());
    }

    /** Cap potion-origin vermin, leaving native spawns untouched. */
    private static boolean limitPotionVermin(EntityJoinLevelEvent event, ServerLevel level) {
        Entity creature = event.getEntity();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(creature.getType());
        if (id == null || !"the_vermin_god".equals(id.getNamespace()) ||
            !VERMIN_ENTITY_NAMES.contains(id.getPath())) return false;
        if (creature.getPersistentData().getBoolean(VERMIN_ORIGIN)) return false;
        // Nearby natural vermin are not counted as potion spawns.
        if (event.loadedFromDisk()) return false;

        MobEffect originalInfection = ForgeRegistries.MOB_EFFECTS.getValue(
            new ResourceLocation("the_vermin_god", "vermin_god"));
        if (originalInfection == null || !VERMIN_POTION_MARKER.isPresent()) return false;
        List<LivingEntity> nearbyVictims = level.getEntitiesOfClass(LivingEntity.class,
            creature.getBoundingBox().inflate(8.0), living ->
                living.isAlive() && living.hasEffect(VERMIN_POTION_MARKER.get()) &&
                living.hasEffect(originalInfection));
        if (nearbyVictims.isEmpty()) return false;

        LivingEntity victim = nearbyVictims.get(0);
        for (LivingEntity nearby : nearbyVictims) {
            if (creature.distanceToSqr(nearby) < creature.distanceToSqr(victim)) victim = nearby;
        }

        AABB region = creature.getBoundingBox().inflate(128.0);
        int owned = 0;
        int local = 0;
        for (Entity candidate : level.getEntitiesOfClass(Entity.class, region,
                 other -> other.getPersistentData().getBoolean(VERMIN_ORIGIN))) {
            local++;
            if (candidate.getPersistentData().hasUUID(VERMIN_OWNER) &&
                victim.getUUID().equals(candidate.getPersistentData().getUUID(VERMIN_OWNER))) owned++;
        }
        if (owned >= VERMIN_PER_VICTIM_CAP || local >= VERMIN_LOCAL_CAP) {
            event.setCanceled(true);
            return true;
        }
        creature.getPersistentData().putBoolean(VERMIN_ORIGIN, true);
        creature.getPersistentData().putUUID(VERMIN_OWNER, victim.getUUID());
        return false;
    }

    /** Invisible Rage selection marker. */
    private static final class RageSelectionMarker extends MobEffect {
        private RageSelectionMarker() {
            super(MobEffectCategory.HARMFUL, 0xB74D45);
        }
    }

    /** Legacy marker for saved entities. */
    private static final class PendingRageEffect extends MobEffect {
        private PendingRageEffect() {
            super(MobEffectCategory.HARMFUL, 0xB74D45);
        }
    }

    /** Keeps potion-only vermin limits active. */
    private static final class VerminPotionMarker extends MobEffect {
        private VerminPotionMarker() {
            super(MobEffectCategory.NEUTRAL, 0x444422);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public void applyEffectTick(LivingEntity living, int amplifier) {
            if (living.level().isClientSide()) return;
            MobEffect infection = ForgeRegistries.MOB_EFFECTS.getValue(
                new ResourceLocation("the_vermin_god", "vermin_god"));
            if (infection == null || !living.hasEffect(infection)) living.removeEffect(this);
        }
    }

    /** Plays SCP-066 music without Deafness. */
    private static final class AcousticTraumaMarker extends MobEffect {
        private AcousticTraumaMarker() {
            super(MobEffectCategory.HARMFUL, 0x9E7086);
        }
    }

    /** Hidden High dialogue marker. */
    private static final class HighDialogueMarker extends MobEffect {
        private HighDialogueMarker() {
            super(MobEffectCategory.NEUTRAL, 0x729B68);
        }
    }

    /** SCP-012 Bleeding without native global sound or player-variable writes. */
    private static final class Scp012BleedingEffect extends MobEffect {
        private Scp012BleedingEffect() {
            super(MobEffectCategory.HARMFUL, 0x991E26);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return duration % 40 == 0;
        }

        @Override
        public void applyEffectTick(LivingEntity living, int amplifier) {
            if (!(living.level() instanceof ServerLevel server) || !living.isAlive()) return;
            living.hurt(living.damageSources().magic(), 1.0F);
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                living.getX(), living.getY() + living.getBbHeight() * 0.65, living.getZ(),
                3, 0.2, 0.2, 0.2, 0.01);
        }
    }

    static record SpecimenSource(String modId, String entityPath, String specimenItem) {}

    static record Provider(String modId, String effectId, String ingredientId) {}

    static record Disease(String effectId, String potionId, Item bookIngredient,
                          int durationTicks, List<Provider> providers) {}

    /** Match potion NBT and the ingredient exactly. */
    private static final class ExactPotionRecipe implements IBrewingRecipe {
        private final Potion input;
        private final Item catalyst;
        private final Potion output;
        private final boolean common;

        private ExactPotionRecipe(Potion input, Item catalyst, Potion output, boolean common) {
            this.input = input;
            this.catalyst = catalyst;
            this.output = output;
            this.common = common;
        }

        @Override
        public boolean isInput(ItemStack stack) {
            return stack.is(Items.POTION) && PotionUtils.getPotion(stack) == input;
        }

        @Override
        public boolean isIngredient(ItemStack stack) {
            return stack.is(catalyst);
        }

        @Override
        public ItemStack getOutput(ItemStack inputStack, ItemStack ingredientStack) {
            if (!isInput(inputStack) || !isIngredient(ingredientStack)) return ItemStack.EMPTY;
            ItemStack result = PotionUtils.setPotion(new ItemStack(Items.POTION), output);
            if (common) result.getOrCreateTag().putInt("CustomPotionColor", COMMON_DISEASE_COLOR);
            return result;
        }
    }
}
