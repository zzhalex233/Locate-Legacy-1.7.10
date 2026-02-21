package com.example.locateleagcy.locate;


public class LocateMessageUtil {

    public static void sendTeleportMessage(net.minecraft.entity.player.EntityPlayer player, int x, int z) {

        net.minecraft.world.World world = player.worldObj;

        int[] tp = com.example.locateleagcy.locate.SafeTeleport.findSafeTeleport(world, x, z, player);

        int tx = tp[0];
        int ty = tp[1];
        int tz = tp[2];

        net.minecraft.util.ChatComponentText prefix = new net.minecraft.util.ChatComponentText("§a找到坐标: ");
        net.minecraft.util.ChatComponentText coords = new net.minecraft.util.ChatComponentText(
            "§e" + tx + " " + ty + " " + tz);

        net.minecraft.util.ChatStyle style = new net.minecraft.util.ChatStyle();
        style.setChatClickEvent(
            new net.minecraft.event.ClickEvent(
                net.minecraft.event.ClickEvent.Action.RUN_COMMAND,
                "/tp " + tx + " " + ty + " " + tz));
        style.setChatHoverEvent(
            new net.minecraft.event.HoverEvent(
                net.minecraft.event.HoverEvent.Action.SHOW_TEXT,
                new net.minecraft.util.ChatComponentText("§7点击传送（自动安全落点）")));

        coords.setChatStyle(style);
        prefix.appendSibling(coords);

        if (tx != x || tz != z) {
            player.addChatMessage(new net.minecraft.util.ChatComponentText("§7(已自动调整到最近陆地)"));
        }

        player.addChatMessage(prefix);
    }
}
