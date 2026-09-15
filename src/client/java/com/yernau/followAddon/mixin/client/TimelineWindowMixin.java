package com.yernau.followAddon.mixin.client;

import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.types.TrackEntityKeyframeType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;
import com.yernau.followAddon.client.FollowCameraController;
import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TimelineWindow.class, remap = false)
public abstract class TimelineWindowMixin {

    @Shadow private static EditorState editorState;
    @Shadow private static EditorScene editorScene;
    @Shadow private static float x;
    @Shadow private static float y;
    @Shadow private static int middleX;
    @Shadow private static int middleY;

    @Shadow
    private static void upgradeToSceneWrite() {
        throw new AssertionError();
    }

    @Inject(method = "renderInner", at = @At("TAIL"))
    private static void followAddon$renderTimelineControl(
            ReplayServer replayServer,
            FlashbackMeta metadata,
            CallbackInfo callbackInfo
    ) {
        int controlSize = ReplayUI.scaleUi(24);
        float buttonX = x + middleX - controlSize;
        float buttonY = y + middleY / 2.0f - controlSize / 2.0f;

        FollowCameraController.CompletedSegment segment = FollowCameraController.renderTimelineButton(
                replayServer,
                editorState,
                buttonX,
                buttonY,
                controlSize
        );
        if (segment == null) {
            return;
        }

        upgradeToSceneWrite();

        TrackEntityKeyframeType type = TrackEntityKeyframeType.INSTANCE;
        int trackIndex = findEnabledTrack(type);
        boolean createTrack = trackIndex < 0;
        if (createTrack) {
            // A following camera must run after ordinary camera tracks so its
            // position is the final camera position applied for the frame.
            trackIndex = editorScene.keyframeTracks.size();
        }

        List<EditorSceneHistoryAction> undo = new ArrayList<>();
        List<EditorSceneHistoryAction> redo = new ArrayList<>();
        if (createTrack) {
            undo.add(new EditorSceneHistoryAction.RemoveTrack(type, trackIndex));
            redo.add(new EditorSceneHistoryAction.AddTrack(type, trackIndex));
        } else {
            addRestoreAction(undo, type, trackIndex, segment.startTick());
            addRestoreAction(undo, type, trackIndex, segment.endTick());
        }
        redo.add(new EditorSceneHistoryAction.SetKeyframe(
                type,
                trackIndex,
                segment.startTick(),
                segment.startKeyframe()
        ));
        redo.add(new EditorSceneHistoryAction.SetKeyframe(
                type,
                trackIndex,
                segment.endTick(),
                segment.endKeyframe()
        ));

        editorScene.push(new EditorSceneHistoryEntry(
                undo,
                redo,
                I18n.get("follow_addon.history.create_segment", segment.targetName())
        ));
        if (createTrack) {
            editorScene.keyframeTracks.get(trackIndex).customName = I18n.get(
                    "follow_addon.track.name",
                    segment.targetName()
            );
        }
        editorState.markDirty();

        ReplayUI.setInfoOverlay(I18n.get(
                "follow_addon.info.segment_created",
                segment.startTick(),
                segment.endTick(),
                segment.targetName()
        ));
    }

    private static int findEnabledTrack(TrackEntityKeyframeType type) {
        for (int index = 0; index < editorScene.keyframeTracks.size(); index++) {
            KeyframeTrack track = editorScene.keyframeTracks.get(index);
            if (track.enabled && track.keyframeType == type) {
                return index;
            }
        }
        return -1;
    }

    private static void addRestoreAction(
            List<EditorSceneHistoryAction> undo,
            TrackEntityKeyframeType type,
            int trackIndex,
            int tick
    ) {
        Keyframe previous = editorScene.keyframeTracks.get(trackIndex).keyframesByTick.get(tick);
        if (previous == null) {
            undo.add(new EditorSceneHistoryAction.RemoveKeyframe(type, trackIndex, tick));
        } else {
            undo.add(new EditorSceneHistoryAction.SetKeyframe(type, trackIndex, tick, previous.copy()));
        }
    }
}
