package com.example.locatelegacy.locate;

import com.example.locatelegacy.config.LocateLegacyConfig;

public class LocateMessageUtil {

    public static void sendTeleportMessage(net.minecraft.entity.player.EntityPlayer player, int x, int z) {

        net.minecraft.world.World world = player.worldObj;

        int[] tp = com.example.locatelegacy.locate.SafeTeleport.findSafeTeleport(world, x, z, player);

        int tx = tp[0];
        int ty = tp[1];
        int tz = tp[2];

        net.minecraft.util.IChatComponent prefix = new net.minecraft.util.ChatComponentTranslation(
            "locatelegacy.msg.found_prefix");

        net.minecraft.util.IChatComponent coords = new net.minecraft.util.ChatComponentText(
            "§e" + tx + " " + ty + " " + tz);

        if (LocateLegacyConfig.clickToTeleport) {

            net.minecraft.util.ChatStyle style = new net.minecraft.util.ChatStyle();
            style.setChatClickEvent(
                new net.minecraft.event.ClickEvent(
                    net.minecraft.event.ClickEvent.Action.RUN_COMMAND,
                    "/tp " + tx + " " + ty + " " + tz));
            style.setChatHoverEvent(
                new net.minecraft.event.HoverEvent(
                    net.minecraft.event.HoverEvent.Action.SHOW_TEXT,
                    new net.minecraft.util.ChatComponentTranslation("locatelegacy.msg.click_to_tp")));

            coords.setChatStyle(style);
        }

        prefix.appendSibling(coords);
        player.addChatMessage(prefix);

        if (tx != x || tz != z) {
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("locatelegacy.msg.adjusted_to_land"));
        }

        double dx = (tx + 0.5D) - player.posX;
        double dz = (tz + 0.5D) - player.posZ;
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

        player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("locatelegacy.msg.distance", dist));
    }
}
