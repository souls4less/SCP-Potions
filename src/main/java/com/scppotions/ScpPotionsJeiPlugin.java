package com.scppotions;

import java.util.ArrayList;
import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional JEI display integration; brewing logic remains in ScpPotions. */
@JeiPlugin
public final class ScpPotionsJeiPlugin implements IModPlugin {
    private static final String SCP1025 = "encyclopedia_of_common_diseases";

    private static final RecipeType<RecipeView> TIPPING =
        RecipeType.create(ScpPotions.MOD_ID, "tipping", RecipeView.class);

    private record RecipeView(ItemStack input, ItemStack reagent, ItemStack output) {}

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ScpPotions.MOD_ID, "jei_integration");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (ScpPotions.activeDiseases().isEmpty()) return;
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        // Reuse JEI's Brewing category.
        registration.addRecipeCategories(new SimpleCategory(
            gui, TIPPING, Items.CRAFTING_TABLE, "jei.scp_potions.tipping"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (ScpPotions.activeDiseases().isEmpty()) return;
        registration.addRecipeCatalyst(new ItemStack(Items.CRAFTING_TABLE), TIPPING);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (ScpPotions.activeDiseases().isEmpty()) return;
        List<IJeiBrewingRecipe> brewing = new ArrayList<>();
        List<RecipeView> arrows = new ArrayList<>();

        ItemStack commonStack = null;
        ItemStack longCommonStack = null;
        if (ScpPotions.hasEncyclopedia()) {
            Item book = ForgeRegistries.ITEMS.getValue(new ResourceLocation(SCP1025, SCP1025));
            Potion common = getPotion("common_diseases");
            Potion longCommon = getPotion("long_common_diseases");
            if (book != null && book != Items.AIR && common != null && longCommon != null) {
                commonStack = stack(common);
                commonStack.getOrCreateTag().putInt("CustomPotionColor", 0x795390);
                longCommonStack = stack(longCommon);
                longCommonStack.getOrCreateTag().putInt("CustomPotionColor", 0x795390);
                brewing.add(registration.getVanillaRecipeFactory().createBrewingRecipe(
                    List.of(stack(Potions.AWKWARD)), new ItemStack(book), commonStack.copy(),
                    recipeId("common_diseases")));
                brewing.add(registration.getVanillaRecipeFactory().createBrewingRecipe(
                    List.of(commonStack.copy()), new ItemStack(Items.REDSTONE), longCommonStack.copy(),
                    recipeId("long_common_diseases")));
                addArrowView(arrows, common);
                addArrowView(arrows, longCommon);
            }
        }

        for (ScpPotions.Disease disease : ScpPotions.activeDiseases()) {
            Potion potion = ScpPotions.potionFor(disease, false);
            Potion longPotion = ScpPotions.potionFor(disease, true);
            if (potion == null || ScpPotions.getEffect(disease) == null) continue;
            ItemStack result = stack(potion);
            String key = disease.potionId();

            // The Encyclopedia route uses the preferred installed effect.
            if (commonStack != null && disease.bookIngredient() != null) {
                brewing.add(registration.getVanillaRecipeFactory().createBrewingRecipe(
                    List.of(commonStack.copy()), new ItemStack(disease.bookIngredient()),
                    result.copy(), recipeId("book_" + key)));
                brewing.add(registration.getVanillaRecipeFactory().createBrewingRecipe(
                    List.of(longCommonStack.copy()), new ItemStack(disease.bookIngredient()),
                    stack(longPotion != null ? longPotion : potion),
                    recipeId("long_book_" + key)));
            }

            // Show standalone brewing, not entity-sampling interactions.
            if (!ScpPotions.isNativeFlesh(disease)) {
                for (ScpPotions.Provider provider : ScpPotions.standaloneProviders(disease)) {
                    Item ingredient = ScpPotions.ingredientFor(provider);
                    if (ingredient == Items.AIR) continue;
                    brewing.add(registration.getVanillaRecipeFactory().createBrewingRecipe(
                        List.of(stack(Potions.THICK)), new ItemStack(ingredient),
                        result.copy(),
                        recipeId("standalone_" + provider.modId() + "_" + key)));
                }
            }

            if (longPotion != null) {
                brewing.add(registration.getVanillaRecipeFactory().createBrewingRecipe(
                    List.of(result.copy()), new ItemStack(Items.REDSTONE), stack(longPotion),
                    recipeId("long_" + key)));
            }
            addArrowView(arrows, potion);
            if (longPotion != null) addArrowView(arrows, longPotion);
        }

        // Display Flesh That Hates' native tagged-ingredient recipe.
        if (ModList.get().isLoaded("flesh_that_hates")) {
            Potion nativePotion = ForgeRegistries.POTIONS.getValue(
                new ResourceLocation("flesh_that_hates", "flesh_infection_potion"));
            Item flesh = ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("flesh_that_hates", "flesh"));
            if (nativePotion != null && flesh != null && flesh != Items.AIR) {
                brewing.add(registration.getVanillaRecipeFactory().createBrewingRecipe(
                    List.of(stack(Potions.AWKWARD)), new ItemStack(flesh), stack(nativePotion),
                    recipeId("native_flesh_infection")));
                brewing.add(registration.getVanillaRecipeFactory().createBrewingRecipe(
                    List.of(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.AWKWARD)),
                    new ItemStack(flesh),
                    PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), nativePotion),
                    recipeId("native_flesh_infection_splash")));
                brewing.add(registration.getVanillaRecipeFactory().createBrewingRecipe(
                    List.of(PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.AWKWARD)),
                    new ItemStack(flesh),
                    PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), nativePotion),
                    recipeId("native_flesh_infection_lingering")));
            }
        }
        // Use JEI's standard brewing layout.
        registration.addRecipes(RecipeTypes.BREWING, brewing);
        registration.addRecipes(TIPPING, arrows);
    }

    private static Potion getPotion(String name) {
        return ForgeRegistries.POTIONS.getValue(
            new ResourceLocation(ScpPotions.MOD_ID, name));
    }

    private static ItemStack stack(Potion potion) {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), potion);
    }

    private static ResourceLocation recipeId(String name) {
        return new ResourceLocation(ScpPotions.MOD_ID, "brewing/" + name);
    }

    private static void addArrowView(List<RecipeView> arrows, Potion potion) {
        arrows.add(new RecipeView(
            PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), potion),
            new ItemStack(Items.ARROW, 8),
            PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW, 8), potion)));
    }

    private static final class SimpleCategory implements IRecipeCategory<RecipeView> {
        private final RecipeType<RecipeView> type;
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        SimpleCategory(IGuiHelper gui, RecipeType<RecipeView> type, Item iconItem, String titleKey) {
            this.type = type;
            this.background = gui.createBlankDrawable(150, 48);
            this.icon = gui.createDrawableItemStack(new ItemStack(iconItem));
            this.title = Component.translatable(titleKey);
        }

        @Override public RecipeType<RecipeView> getRecipeType() { return type; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeView recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 14, 15).addItemStack(recipe.input());
            builder.addSlot(RecipeIngredientRole.INPUT, 64, 15).addItemStack(recipe.reagent());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 114, 15).addItemStack(recipe.output());
        }
    }
}
