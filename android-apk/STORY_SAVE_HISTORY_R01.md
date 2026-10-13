# SaveStats và Milestone / Level history

Game Core giữ committedEvents, defeatedBosses, completedCheckpoints và completedLevels.
Trong Checkpoint: các applied IDs và eventRecords xác nhận lượt đã chạy.
Sau Checkpoint: story.checkpointHistory giữ eventRefs và events thật (Turn, action,
event, outcome, Entity/encounter ID), Boss defeat và completion.
Level history giữ checkpointRefs và số event đã xảy ra.
StoryMilestoneRuntime.continuityContext đọc tối đa 12 event đã commit từ SaveState
cho GM và AI dựng khung, không có sổ storyFacts song song.
Manual save/load xác thực Turn, encounter, Boss award và lịch sử.
Không migration save cũ; CI không thay thế nghiệm thu gameplay trên Android.
