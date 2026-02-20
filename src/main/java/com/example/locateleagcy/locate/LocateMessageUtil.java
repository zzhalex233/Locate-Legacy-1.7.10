package com.example.locateleagcy.locate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;

/**
 * 聊天消息工具：发送可点击坐标（点击执行 /tp）
 */
public class LocateMessageUtil {

    public static void sendTeleportMessage(EntityPlayer player, int x, int y, int z) {

        ChatComponentText prefix = new ChatComponentText("§a找到坐标: ");
        ChatComponentText coords = new ChatComponentText("§e" + x + " " + y + " " + z);

        ChatStyle style = new ChatStyle();
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + x + " " + y + " " + z));
        style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("§7点击传送")));

        coords.setChatStyle(style);
        prefix.appendSibling(coords);

        player.addChatMessage(prefix);
    }
}
