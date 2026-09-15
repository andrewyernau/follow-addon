package com.yernau.followAddon.client;

import com.moulberry.flashback.combo_options.TrackingBodyPart;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.impl.TrackEntityKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import imgui.moulberry90.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Owns the small, transient two-click workflow shown in Flashback's timeline.
 * The resulting keyframes are native Flashback keyframes, so playback, export,
 * undo/redo and editor-state persistence remain Flashback's responsibility.
 */
public final class FollowCameraController {

    private static final String POPUP_ID = "##FollowAddonCreateSegment";
    private static PendingStart pendingStart;

    private FollowCameraController() {
    }

    public static CompletedSegment renderTimelineButton(
            ReplayServer replayServer,
            EditorState editorState,
            float buttonX,
            float buttonY,
            float buttonSize
    ) {
        UUID replayId = replayServer.getMetadata().replayIdentifier;
        if (pendingStart != null && !pendingStart.replayId.equals(replayId)) {
            pendingStart = null;
        }

        float oldCursorX = ImGui.getCursorPosX();
        float oldCursorY = ImGui.getCursorPosY();
        ImGui.setCursorScreenPos(buttonX, buttonY);

        if (ImGui.button("\ue55f##FollowAddonTimelineButton", buttonSize, buttonSize)) {
            ImGui.openPopup(POPUP_ID);
        }
        ImGuiHelper.tooltip(pendingStart == null
                ? I18n.get("follow_addon.timeline.start_tooltip")
                : I18n.get("follow_addon.timeline.finish_tooltip", pendingStart.startTick));

        CompletedSegment completed = renderPopup(replayServer, editorState, replayId);

        ImGui.setCursorPosX(oldCursorX);
        ImGui.setCursorPosY(oldCursorY);
        return completed;
    }

    private static CompletedSegment renderPopup(ReplayServer replayServer, EditorState editorState, UUID replayId) {
        if (!ImGuiHelper.beginPopup(POPUP_ID)) {
            return null;
        }

        CompletedSegment completed = null;
        if (pendingStart == null) {
            renderTargetPicker(replayServer, editorState, replayId);
        } else {
            completed = renderFinishStep(replayServer, editorState);
        }

        ImGui.endPopup();
        return completed;
    }

    private static void renderTargetPicker(ReplayServer replayServer, EditorState editorState, UUID replayId) {
        ImGui.textUnformatted(I18n.get("follow_addon.popup.choose_player"));
        ImGui.separator();

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        List<Player> players = new ArrayList<>();
        if (level != null) {
            for (Player player : level.players()) {
                if (player != minecraft.player) {
                    players.add(player);
                }
            }
        }
        players.sort(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER));

        if (players.isEmpty()) {
            ImGui.textDisabled(I18n.get("follow_addon.popup.no_players"));
        }

        for (Player player : players) {
            String label = player.getName().getString() + "##FollowTarget-" + player.getUUID();
            if (ImGui.selectable(label)) {
                TrackEntityKeyframe start = capture(player, editorState);
                if (start == null) {
                    ReplayUI.setInfoOverlay(I18n.get("follow_addon.error.camera_too_close"));
                } else {
                    pendingStart = new PendingStart(
                            replayId,
                            replayServer.getReplayTick(),
                            player.getUUID(),
                            player.getName().getString(),
                            start
                    );
                    ReplayUI.setInfoOverlayShort(I18n.get(
                            "follow_addon.info.start_marked",
                            pendingStart.startTick,
                            pendingStart.targetName
                    ));
                }
                ImGui.closeCurrentPopup();
            }
        }

        ImGui.separator();
        if (ImGui.button(I18n.get("gui.cancel"))) {
            ImGui.closeCurrentPopup();
        }
    }

    private static CompletedSegment renderFinishStep(ReplayServer replayServer, EditorState editorState) {
        PendingStart start = pendingStart;
        int endTick = replayServer.getReplayTick();

        ImGui.textUnformatted(I18n.get("follow_addon.popup.target", start.targetName));
        ImGui.textUnformatted(I18n.get("follow_addon.popup.start_tick", start.startTick));
        ImGui.textUnformatted(I18n.get("follow_addon.popup.end_tick", endTick));
        ImGui.separator();

        boolean invalidRange = endTick <= start.startTick;
        if (invalidRange) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(I18n.get("follow_addon.popup.mark_end"))) {
            Entity target = findEntity(start.targetUuid);
            TrackEntityKeyframe end = target == null ? null : capture(target, editorState);
            if (end == null) {
                ReplayUI.setInfoOverlay(I18n.get("follow_addon.error.target_unavailable"));
            } else {
                unwrapYaw(start.startKeyframe, end);
                pendingStart = null;
                ImGui.closeCurrentPopup();
                return new CompletedSegment(
                        start.startTick,
                        endTick,
                        start.targetName,
                        start.startKeyframe,
                        end
                );
            }
        }
        if (invalidRange) {
            ImGui.endDisabled();
            ImGuiHelper.tooltip(I18n.get("follow_addon.error.end_before_start"));
        }

        ImGui.sameLine();
        if (ImGui.button(I18n.get("follow_addon.popup.discard_start"))) {
            pendingStart = null;
            ImGui.closeCurrentPopup();
        }

        return null;
    }

    private static Entity findEntity(UUID uuid) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        for (Player player : level.players()) {
            if (player.getUUID().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    private static TrackEntityKeyframe capture(Entity target, EditorState editorState) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (camera == null) {
            return null;
        }

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 targetRoot = target.getPosition(partialTick);
        double anchorHeight = target.getEyeHeight();
        Vec3 targetAnchor = targetRoot.add(0.0, anchorHeight, 0.0);
        Vec3 cameraPosition = camera.getEyePosition(partialTick);
        Vec3 targetToCamera = cameraPosition.subtract(targetAnchor);
        double distance = targetToCamera.length();
        if (distance < 0.05) {
            return null;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(targetToCamera.x, -targetToCamera.z));
        double vertical = Math.max(-1.0, Math.min(1.0, targetToCamera.y / distance));
        float pitch = (float) Math.toDegrees(Math.asin(vertical));
        float roll = editorState.replayVisuals.overrideRoll ? editorState.replayVisuals.overrideRollAmount : 0.0f;

        return new TrackEntityKeyframe(
                target.getUUID(),
                TrackingBodyPart.ROOT,
                yaw,
                pitch,
                new Vector3d(0.0, anchorHeight, 0.0),
                new Vector3d(0.0, 0.0, distance),
                roll,
                InterpolationType.LINEAR
        );
    }

    /** Chooses the shortest rotation between independently captured endpoints. */
    private static void unwrapYaw(TrackEntityKeyframe start, TrackEntityKeyframe end) {
        while (end.yawOffset - start.yawOffset > 180.0f) {
            end.yawOffset -= 360.0f;
        }
        while (end.yawOffset - start.yawOffset < -180.0f) {
            end.yawOffset += 360.0f;
        }
    }

    private record PendingStart(
            UUID replayId,
            int startTick,
            UUID targetUuid,
            String targetName,
            TrackEntityKeyframe startKeyframe
    ) {
    }

    public record CompletedSegment(
            int startTick,
            int endTick,
            String targetName,
            TrackEntityKeyframe startKeyframe,
            TrackEntityKeyframe endKeyframe
    ) {
    }
}
