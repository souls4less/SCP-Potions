package com.scppotions;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Tint vanilla potion-bottle models for specimen items. */
@Mod.EventBusSubscriber(modid = ScpPotions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ScpPotionsClient {
    private static final Map<String, Integer> SPECIMEN_COLOURS = Map.of(
        "amnestic_residue", 0xB0A3DD,
        "corrosive_secretion", 0x648E3B,
        "infected_eye_fluid", 0x8A9A3C,
        "nightmare_residue", 0x4C336A,
        "anomalous_snow", 0xC4E8EF,
        "hive_secretion", 0xCC8C35,
        "infested_specimen", 0x604231,
        "acidic_secretion", 0x89D73E,
        "frozen_venom", 0x59C8E9
    );

    private ScpPotionsClient() { }

    @SubscribeEvent
    public static void registerItemColours(RegisterColorHandlersEvent.Item event) {
        for (Map.Entry<String, Integer> entry : SPECIMEN_COLOURS.entrySet()) {
            ResourceLocation id = new ResourceLocation(ScpPotions.MOD_ID, entry.getKey());
            // Register only specimens from installed mods.
            if (!ForgeRegistries.ITEMS.containsKey(id)) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) continue;
            int liquidColour = entry.getValue();
            event.register((stack, layer) -> layer == 0 ? liquidColour : 0xFFFFFF, item);
        }
    }
}
