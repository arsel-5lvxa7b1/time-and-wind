package ru.aiefu.timeandwind;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TAWCommands {
    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher){
        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("reload").executes(context -> reloadCfg(context.getSource()))));
        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("get-current-world-id").executes(context -> printCurrentWorldId(context.getSource()))));

        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("parse-world_ids").executes(context -> parseWorldsIds(context.getSource()))));

        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("get-ambient-darkness").executes(context -> printAmbientDarkness(context.getSource()))));
        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("get-light-level").executes(context -> getLightLevel(context.getSource()))));
    }

    public static int reloadCfg(ServerCommandSource source) throws CommandSyntaxException {
        if(source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            MinecraftServer server = source.getMinecraftServer();
            IOManager.readTimeData();
            source.getMinecraftServer().getWorlds().forEach(serverWorld -> {
                String id = serverWorld.getRegistryKey().getValue().toString();
                if (TimeAndWind.timeDataMap.containsKey(id)) {
                    ((IDimType) serverWorld.getDimension()).setCycleDuration(TimeAndWind.timeDataMap.get(id).dayDuration, TimeAndWind.timeDataMap.get(id).nightDuration);
                }
                TAWScheduler.createTAWSchedule(serverWorld.getDimension(), serverWorld.getRegistryKey().getValue().getPath(), "_villager_taw", false);
                TAWScheduler.createTAWSchedule(serverWorld.getDimension(), serverWorld.getRegistryKey().getValue().getPath(), "_villager_baby_taw", true);
                List<Entity> villagers  = serverWorld.getEntitiesByType(EntityType.VILLAGER, entity -> true);
                for(Entity e : villagers){
                    ((VillagerEntity)e).reinitializeBrain(serverWorld);
                }
            });
            for(ServerPlayerEntity player : server.getPlayerManager().getPlayerList()){
               TimeAndWind.sendConfigSyncPacket(player);
            }
            source.sendFeedback(new LiteralText("[Time & Wind] Config reloaded"), true);
        }
        else {
            source.sendError(new LiteralText("[Time & Wind] Permission level of 4 is required to run this command"));
        }
        return 0;
    }
    public static int printCurrentWorldId(ServerCommandSource source) throws CommandSyntaxException {
        source.sendFeedback(new LiteralText(source.getPlayer().world.getRegistryKey().getValue().toString()), false);
        return 0;
    }
    public static int printAmbientDarkness(ServerCommandSource source) throws CommandSyntaxException {
        source.sendFeedback(new LiteralText(source.getPlayer().world.getAmbientDarkness() + ""), false);
        return 0;
    }
    public static int parseWorldsIds(ServerCommandSource source) {
        List<String> ids = new ArrayList<>();
        source.getMinecraftServer().getWorlds().forEach(serverWorld -> ids.add(serverWorld.getRegistryKey().getValue().toString()));
        File file = new File("taw-worlds-ids.json");
        new IOManager().fileWriter(file, new GsonBuilder().setPrettyPrinting().create().toJson(ids));
        source.sendFeedback(new LiteralText("Saved to " + file.getAbsolutePath()), false);
        return 0;
    }
    public static int getLightLevel(ServerCommandSource source) throws CommandSyntaxException {
        source.sendFeedback(new LiteralText(source.getPlayer().world.getLightLevel(source.getPlayer().getBlockPos()) + ""), false);
        return 0;
    }
}
