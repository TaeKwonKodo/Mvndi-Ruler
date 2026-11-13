package com.example.blockruler;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class BlockRulerClient implements ClientModInitializer {
    /** 0 = first block is labelled 0 (pure distance). Set to 1 to label the first block "1". */
    private static final int START_AT = 0;
    private static final int MAX_BLOCKS = 256;

    private static BlockPos a, b;
    private static KeyBinding setKey, clearKey;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("blockruler", "main"));
        setKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.blockruler.set", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, cat));
        clearKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.blockruler.clear", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, cat));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::render);
    }

    private void tick(MinecraftClient mc) {
        if (mc.player == null) return;

        while (clearKey.wasPressed()) {
            a = null; b = null;
            mc.player.sendMessage(Text.literal("Ruler cleared"), true);
        }
        while (setKey.wasPressed()) {
            if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) continue;
            BlockPos p = hit.getBlockPos();
            if (a == null || b != null) { a = p; b = null; }   // start a new measurement
            else b = p;
            if (b != null) {
                int dx = Math.abs(b.getX() - a.getX()), dy = Math.abs(b.getY() - a.getY()), dz = Math.abs(b.getZ() - a.getZ());
                double euclid = Math.sqrt(dx * dx + dy * dy + dz * dz);
                mc.player.sendMessage(Text.literal(String.format(
                        "Ruler: X %d  Y %d  Z %d  |  Straight %.2f  |  Blocks %d",
                        dx, dy, dz, euclid, Math.max(dx, Math.max(dy, dz)))), true);
            } else {
                mc.player.sendMessage(Text.literal("Ruler: point A set, pick point B"), true);
            }
        }
    }

    private static List<BlockPos> line() {
        List<BlockPos> out = new ArrayList<>();
        if (a == null) return out;
        if (b == null) { out.add(a); return out; }
        int dx = b.getX() - a.getX(), dy = b.getY() - a.getY(), dz = b.getZ() - a.getZ();
        int n = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        n = Math.min(n, MAX_BLOCKS);
        if (n == 0) { out.add(a); return out; }
        for (int i = 0; i <= n; i++) {
            out.add(new BlockPos(
                    a.getX() + Math.round((float) dx * i / n),
                    a.getY() + Math.round((float) dy * i / n),
                    a.getZ() + Math.round((float) dz * i / n)));
        }
        return out;
    }

    private void render(WorldRenderContext ctx) {
        List<BlockPos> blocks = line();
        if (blocks.isEmpty()) return;

        MatrixStack matrices = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d cam = ctx.camera().getPos();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        for (int i = 0; i < blocks.size(); i++) {
            BlockPos p = blocks.get(i);
            boolean end = (i == 0) || (i == blocks.size() - 1 && b != null);

            // highlight box (endpoints green, middle blocks yellow)
            Box box = new Box(p).expand(0.002).offset(-cam.x, -cam.y, -cam.z);
            if (end) VertexRendering.drawBox(matrices, lines, box, 0.2f, 1f, 0.2f, 1f);
            else     VertexRendering.drawBox(matrices, lines, box, 1f, 0.9f, 0.1f, 1f);

            // number label, billboarded toward the camera, visible through blocks
            String label = String.valueOf(i + START_AT);
            matrices.push();
            matrices.translate(p.getX() + 0.5 - cam.x, p.getY() + 0.5 - cam.y, p.getZ() + 0.5 - cam.z);
            matrices.multiply(ctx.camera().getRotation());
            matrices.scale(-0.04f, -0.04f, 0.04f);
            tr.draw(label, -tr.getWidth(label) / 2f, -4f, 0xFFFFFFFF, true,
                    matrices.peek().getPositionMatrix(), consumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();
        }
    }
}
