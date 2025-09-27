package net.vansen.neonpaper.usage;

import com.mojang.brigadier.context.CommandContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.commands.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class NeonPaperUsage {

    public static int usage(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getBukkitSender();

        s.sendRichMessage("<#80d0ff><bold>NeonPaper Commands:</bold>");

        configuration(ctx);
        map(ctx);
        information(ctx);
        teleportation(ctx);
        region(ctx);

        return 1;
    }

    public static int configuration(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getBukkitSender();

        s.sendRichMessage("");
        s.sendRichMessage("<#80d0ff>Configuration");
        s.sendMessage(Component.text("  • reload: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Reload configuration and start AutoRegeneration", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper reload")));

        return 1;
    }

    public static int map(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getBukkitSender();

        s.sendRichMessage("");
        s.sendRichMessage("<#80d0ff>Map Operations");
        s.sendMessage(Component.text("  • flushmap: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Flush pending chunks to disk", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper map flushmap")));
        s.sendMessage(Component.text("  • loadmap: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Load pending chunks from disk", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper map loadmap")));

        return 1;
    }

    public static int information(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getBukkitSender();

        s.sendRichMessage("");
        s.sendRichMessage("<#80d0ff>Region Info");
        s.sendMessage(Component.text("  • info <name>: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Display detailed info about a region", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper information info ")));
        s.sendMessage(Component.text("  • inside <name>: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Check if you are inside a region", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper information inside ")));
        s.sendMessage(Component.text("  • insideregions: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("List all regions you are currently inside", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper information insideregions")));

        return 1;
    }

    public static int teleportation(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getBukkitSender();

        s.sendRichMessage("");
        s.sendRichMessage("<#80d0ff>Teleportation");
        s.sendMessage(Component.text("  • tp <name> pos|center: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Teleport to a region's pos1, pos2, or center", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper teleportation ")));

        return 1;
    }

    public static int region(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getBukkitSender();

        s.sendRichMessage("");
        s.sendRichMessage("<#80d0ff>Region Management");
        s.sendMessage(Component.text("  • pos <1|2> [x y z]: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Set positions of the region you want to save", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper region pos ")));
        s.sendMessage(Component.text("  • save <name>: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Save region snapshot", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper region save ")));
        s.sendMessage(Component.text("  • delete <name>: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Delete a region snapshot", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper region delete ")));
        s.sendMessage(Component.text("  • paste <name>: ", TextColor.fromHexString("#a1ceff"))
                .append(Component.text("Paste a region snapshot", TextColor.fromHexString("#80d0ff")))
                .clickEvent(ClickEvent.suggestCommand("/neonpaper region paste ")));

        return 1;
    }
}
