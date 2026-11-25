package com.cyberday1.riftfall.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Attribution travels with the mod and is accessible beside the sky from the pause menu. */
@EventBusSubscriber(modid="riftfall", value=Dist.CLIENT)
public final class SkyCreditsScreen extends Screen {
    private static final String KEY = "screen.riftfall.sky_credits.";
    private final Screen parent;
    private SkyCreditsScreen(Screen parent) { super(Component.translatable(KEY + "title")); this.parent=parent; }

    @SubscribeEvent
    public static void pauseMenu(ScreenEvent.Init.Post event) {
        var client=Minecraft.getInstance();
        if(!(event.getScreen() instanceof PauseScreen) || client.level==null) return;
        var id=client.level.dimension().location();
        if(!id.getNamespace().equals("riftfall") || !id.getPath().startsWith("shattered_home/")) return;
        Screen parent=event.getScreen();
        event.addListener(Button.builder(Component.translatable(KEY + "open"), b -> client.setScreen(new SkyCreditsScreen(parent)))
                .bounds(6,parent.height-26,120,20).build());
    }

    @Override protected void init() {
        int y=height/2;
        addRenderableWidget(Button.builder(Component.translatable(KEY + "nasa_link"),
                ConfirmLinkScreen.confirmLink(this,"https://svs.gsfc.nasa.gov/4851/"))
                .bounds(width/2-155,y+18,150,20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "eso_link"),
                ConfirmLinkScreen.confirmLink(this,"https://www.eso.org/public/images/eso1828a/"))
                .bounds(width/2+5,y+18,150,20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "license_link"),
                ConfirmLinkScreen.confirmLink(this,"https://creativecommons.org/licenses/by/4.0/"))
                .bounds(width/2-100,y+42,200,20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK,b -> onClose())
                .bounds(width/2-60,height-28,120,20).build());
    }

    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderBackground(graphics,mouseX,mouseY,partialTick);
        int y=height/2;
        graphics.drawCenteredString(font,title,width/2,y-88,0xFFFFFF);
        graphics.drawCenteredString(font,Component.translatable(KEY + "stars"),width/2,y-64,0xDDEEFF);
        graphics.drawCenteredString(font,Component.translatable(KEY + "stars_map"),width/2,y-52,0xDDEEFF);
        graphics.drawCenteredString(font,Component.translatable(KEY + "carina"),width/2,y-32,0xDDEEFF);
        graphics.drawCenteredString(font,Component.translatable(KEY + "adapted"),width/2,y-12,0xBBBBBB);
        graphics.drawCenteredString(font,Component.translatable(KEY + "no_endorsement"),width/2,y,0xBBBBBB);
        super.render(graphics,mouseX,mouseY,partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
