package com.example.locateleagcy.locate;

/**
 * 聊天消息工具：发送可点击坐标（点击执行 /tp）
 * 自动选择安全落点；如果在流体上，自动找最近陆地（会改变 X/Z）
 */
public class LocateMessageUtil {

    public static void sendTeleportMessage(net.minecraft.entity.player.EntityPlayer player, int x, int z) {

        net.minecraft.world.World world = player.worldObj;

        // ✅ 使用“会改 X/Z 找陆地”的安全传送逻辑（并修复下界最高点基岩问题）
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

        // 如果为了上岸改了坐标，提示一下（很短，不啰嗦）
        if (tx != x || tz != z) {
            player.addChatMessage(new net.minecraft.util.ChatComponentText("§7(已自动调整到最近陆地)"));
        }

        player.addChatMessage(prefix);
    }
}
