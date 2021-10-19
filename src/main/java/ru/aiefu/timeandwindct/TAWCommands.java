package ru.aiefu.timeandwindct;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.world.GameRules;
import ru.aiefu.timeandwindct.config.TimeDataStorage;
import ru.aiefu.timeandwindct.tickers.DefaultTicker;
import ru.aiefu.timeandwindct.tickers.SystemTimeTicker;
import ru.aiefu.timeandwindct.tickers.Ticker;
import ru.aiefu.timeandwindct.tickers.TimeTicker;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TAWCommands {
    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher){
        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("reload").executes(context -> reloadCfg(context.getSource()))));
        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("enable-debug").then(CommandManager.argument("boolean", BoolArgumentType.bool()).executes(context ->
                enableDebug(context.getSource(), BoolArgumentType.getBool(context, "boolean"))))));

        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("set-cycle-length").
                then(CommandManager.argument("dimension", DimensionArgumentType.dimension()).then(CommandManager.argument("day_length", LongArgumentType.longArg(1)).
                then(CommandManager.argument("night_length", LongArgumentType.longArg(1)).executes(context ->
                        setTimeLength(DimensionArgumentType.getDimensionArgument(context, "dimension"), context.getSource(),
                                LongArgumentType.getLong(context, "day_length"), LongArgumentType.getLong(context,"night_length"))))))));
        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("remove-cycle-entry").
                then(CommandManager.argument("dimension", DimensionArgumentType.dimension()).executes(context ->
                        removeConfigEntry(context.getSource(), DimensionArgumentType.getDimensionArgument(context, "dimension"))))));

        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("switch-system-time-sync").
                then(CommandManager.argument("ssyncstate", BoolArgumentType.bool()).executes(context ->
                        switchSystemTimeSyncState(context.getSource(), BoolArgumentType.getBool(context,"ssyncstate"))))));

        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("get-current-world-id").executes(context -> printCurrentWorldId(context.getSource()))));

        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("parse-worlds-ids").executes(context -> parseWorldsIds(context.getSource()))));

        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("get-ambient-darkness").executes(context -> printAmbientDarkness(context.getSource()))));
        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("get-light-level").executes(context -> getLightLevel(context.getSource()))));

        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("get-time-data").executes(context -> getTimeConfig(context.getSource()))));

        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("time-ticker-values").executes(context -> getTimeChecker(context.getSource()))));
        dispatcher.register(CommandManager.literal("taw").then(CommandManager.literal("system-time").executes(context -> getSystemTime(context.getSource()))));
    }

    private static int enableDebug(ServerCommandSource source, boolean bl) throws CommandSyntaxException {
        if(source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            TimeAndWindCT.debugMode = bl;
            source.sendFeedback(new LiteralText("[Time & Wind] Set debug mod to " + bl), true);
        } else source.sendError(new LiteralText("[Time & Wind] Permission level of 4 is required to run this command"));
        return 0;
    }

    private static int setTimeLength(ServerWorld targetWorld, ServerCommandSource source, long dayD, long nightD) throws CommandSyntaxException {
        if(source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            String worldId = targetWorld.getRegistryKey().getValue().toString();
            IOManager.updateTimeData(worldId, dayD, nightD);
            source.sendFeedback(new LiteralText("Configuration entry added, now use /taw reload to apply changes"), false);
        } else source.sendError(new LiteralText("[Time & Wind] Permission level of 4 is required to run this command"));
        return 0;
    }

    private static int removeConfigEntry(ServerCommandSource source, ServerWorld targetWorld) throws CommandSyntaxException {
        if(source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            String worldId = targetWorld.getRegistryKey().getValue().toString();
            if(TimeAndWindCT.timeDataMap.containsKey(worldId)){
                TimeAndWindCT.timeDataMap.remove(worldId);
                IOManager.updateTimeData();
                source.sendFeedback(new LiteralText("Entry removed, now use /taw reload to apply changes"), false);
            } else source.sendError(new LiteralText("Config does not contains settings for " + worldId));
        } else source.sendError(new LiteralText("[Time & Wind] Permission level of 4 is required to run this command"));
        return 0;
    }

    private static int switchSystemTimeSyncState(ServerCommandSource source, boolean state) throws CommandSyntaxException {
        if(source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            TimeAndWindCT.CONFIG.syncWithSystemTime = state;
            source.getMinecraftServer().getGameRules().get(GameRules.DO_INSOMNIA).set(!state, source.getMinecraftServer());
            IOManager.updateSysTimeCfg();
            source.sendFeedback(new LiteralText("SysTimeSync state switched to" + state + " now use /taw reload to apply changes"), false);
        } else source.sendError(new LiteralText("[Time & Wind] Permission level of 4 is required to run this command"));
        return 0;
    }

    private static int reloadCfg(ServerCommandSource source) throws CommandSyntaxException {
        if(source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            MinecraftServer server = source.getMinecraftServer();
            int result = IOManager.readTimeData();
            TimeAndWindCT.CONFIG = IOManager.readModConfig();
            TimeAndWindCT.systemTimeConfig = IOManager.readSysTimeCfg();
            if(result == 0){
                source.sendFeedback(new LiteralText("Unable to reload config"), false);
                return 0;
            }
            source.getMinecraftServer().getWorlds().forEach(serverWorld -> {
                String id = serverWorld.getRegistryKey().getValue().toString();
                if(TimeAndWindCT.CONFIG.syncWithSystemTime){
                    ((ITimeOperations) serverWorld).setTimeTicker(new SystemTimeTicker((ITimeOperations) serverWorld));
                }
                else if (TimeAndWindCT.timeDataMap.containsKey(id)) {
                    TimeDataStorage storage = TimeAndWindCT.timeDataMap.get(id);
                    ((ITimeOperations) serverWorld).setTimeTicker(new TimeTicker(storage.dayDuration, storage.nightDuration));
                } else ((ITimeOperations) serverWorld).setTimeTicker(new DefaultTicker());
            });
            for(ServerPlayerEntity player : server.getPlayerManager().getPlayerList()){
               TimeAndWindCT.sendConfigSyncPacket(player);
            }
            source.sendFeedback(new LiteralText("[Time & Wind] Config reloaded"), true);
        }
        else {
            source.sendError(new LiteralText("[Time & Wind] Permission level of 4 is required to run this command"));
        }
        return 0;
    }
    private static int printCurrentWorldId(ServerCommandSource source) throws CommandSyntaxException {
        if(TimeAndWindCT.debugMode || source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            String id = source.getPlayer().world.getRegistryKey().getValue().toString();
            source.sendFeedback(new LiteralText(id), false);
            ServerPlayNetworking.send(source.getPlayer(), NetworkPacketsID.WORLD_ID_CLIPBOARD, new PacketByteBuf(Unpooled.buffer()).writeString(id));
        }
        return 0;
    }
    private static int printAmbientDarkness(ServerCommandSource source) throws CommandSyntaxException {
        if(TimeAndWindCT.debugMode || source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            source.sendFeedback(new LiteralText("Ambient Darkness: " + source.getPlayer().world.getAmbientDarkness()), false);
        }
        return 0;
    }
    private static int parseWorldsIds(ServerCommandSource source) throws CommandSyntaxException {
        if(source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            List<String> ids = new ArrayList<>();
            source.getMinecraftServer().getWorlds().forEach(serverWorld -> ids.add(serverWorld.getRegistryKey().getValue().toString()));
            File file = new File("taw-worlds-ids.json");
            IOManager.fileWriter(file, new GsonBuilder().setPrettyPrinting().create().toJson(ids));
            source.sendFeedback(new LiteralText("Saved to " + file.getAbsolutePath()), false);
        } else source.sendError(new LiteralText("[Time & Wind] Permission level of 4 is required to run this command"));
        return 0;
    }
    private static int getLightLevel(ServerCommandSource source) throws CommandSyntaxException {
        if(TimeAndWindCT.debugMode || source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            source.sendFeedback(new LiteralText("Light Level: " + source.getPlayer().world.getLightLevel(source.getPlayer().getBlockPos())), false);
        }
        return 0;
    }

    private static int getTimeConfig(ServerCommandSource source) throws CommandSyntaxException {
        if(TimeAndWindCT.debugMode || source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            ServerPlayerEntity player = source.getPlayer();
            String worldId = player.world.getRegistryKey().getValue().toString();
            if (player.world.getDimension().hasFixedTime()) {
                source.sendFeedback(new LiteralText("Current dimension has fixed time, custom configuration is useless"), false);
            } else
                source.sendFeedback(new LiteralText("Current dimension does not has fixed time, custom configuration should work fine"), false);
            if (TimeAndWindCT.timeDataMap.containsKey(worldId)) {
                TimeDataStorage storage = TimeAndWindCT.timeDataMap.get(worldId);
                source.sendFeedback(new LiteralText("Server config for current world: Day Duration: " + storage.dayDuration + " Night Duration: " + storage.nightDuration), true);
                ServerPlayNetworking.send(player, NetworkPacketsID.CFG_DEBUG_INFO, new PacketByteBuf(Unpooled.buffer()));
            } else source.sendError(new LiteralText("No Data found for current world on server side"));
        }
        return 0;
    }

    private static int getTimeChecker(ServerCommandSource source) throws CommandSyntaxException {
        if(TimeAndWindCT.debugMode || source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            Ticker t = ((ITimeOperations) source.getWorld()).getTimeTicker();
            if(t instanceof TimeTicker) {
                TimeTicker ticker = (TimeTicker) t;
                source.sendFeedback(new LiteralText("Day: " + ticker.getDayD() + " Night: " + ticker.getNightD()), false);
                source.sendFeedback(new LiteralText("Day Mod: " + ticker.getDayMod() + " Night Mod: " + ticker.getNightMod()), false);
                source.sendFeedback(new LiteralText("Day RE: " + ticker.getDayRoundingError() + " Night RE: " + ticker.getNightRoundingError()), false);
            } if(t instanceof SystemTimeTicker){
                SystemTimeTicker stt = (SystemTimeTicker) t;
                String sunrise = TimeAndWindCT.getFormattedTime(stt.getSunrise() / 1000);
                String sunset = TimeAndWindCT.getFormattedTime(stt.getSunset() / 1000);
                String dayD = TimeAndWindCT.getFormattedTime(stt.getDayD() / 1000);
                String nightD = TimeAndWindCT.getFormattedTime(stt.getNightD() / 1000);
                source.sendFeedback(new LiteralText("Time is synced with system time"), false);
                source.sendFeedback(new LiteralText("Sunrise are at: " + sunrise + " and sunset are at: " + sunset + " in timezone: " + TimeAndWindCT.systemTimeConfig.timeZone), false);
                source.sendFeedback(new LiteralText("Day Length are: " + dayD + " and Night Length are: " + nightD), false);
            } else source.sendError(new LiteralText("This world uses default time ticker"));
        } else source.sendError(new LiteralText("[Time & Wind] Permission level of 4 is required to run this command"));
        return 0;
    }

    private static int getSystemTime(ServerCommandSource source) throws CommandSyntaxException {
        if(source.hasPermissionLevel(4) || source.getMinecraftServer().isHost(source.getPlayer().getGameProfile())) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            LocalDateTime time = LocalDateTime.now();
            source.sendFeedback(new LiteralText(time.format(formatter)), false);
        } else source.sendError(new LiteralText("[Time & Wind] Permission level of 4 is required to run this command"));
        return 0;
    }

}
