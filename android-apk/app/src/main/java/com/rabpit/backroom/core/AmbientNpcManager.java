package com.rabpit.backroom.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Pure-Java manager for Ambient NPC (random passerby) content.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Build the Gemini generation prompt from exactly two dynamic inputs
 *       (current level id + a short scene snippet). No Game State, no history,
 *       no inventory, no Canon database is ever sent.</li>
 *   <li>Parse and validate the Gemini JSON response. Never throws; returns
 *       {@code null} for any invalid payload.</li>
 *   <li>Hold at most ONE active ambient encounter in RAM. No database, no
 *       CharacterState, no memory/registry. Clearing the cache is permanent:
 *       a later roll creates a brand-new NPC, never a reunion.</li>
 * </ul>
 *
 * <p>Ambient NPCs are narrative-only. They grant no items, EXP, loot, combat,
 * party membership, quests, exits, or level changes. Any generated text that
 * tries to claim such effects is rejected by {@link #parse(String)}.
 *
 * <p>No Android dependencies. Only {@code org.json} + {@code java.util}.
 */
public class AmbientNpcManager {

    // ------------------------------------------------------------------
    // Limits
    // ------------------------------------------------------------------

    /** Max chars for the NPC appearance description. */
    public static final int MAX_DESCRIPTION_LEN = 500;
    /** Max chars for the NPC departure narration. */
    public static final int MAX_EXIT_LEN = 500;
    /** Max chars per dialogue line. */
    public static final int MAX_DIALOGUE_LEN = 300;
    /** Min/max dialogue lines per generated NPC. */
    public static final int MIN_DIALOGUES = 2;
    public static final int MAX_DIALOGUES = 4;

    private static final int MAX_LEVEL_ID_LEN = 64;
    private static final int MAX_SCENE_SNIPPET_LEN = 400;

    // ------------------------------------------------------------------
    // Blocklist: Core-manipulation / gameplay / Canon-claim patterns.
    //
    // Substring match (case-insensitive) over description + dialogues + exit.
    // Covers both English and Vietnamese phrasings Gemini might emit.
    // ------------------------------------------------------------------

    private static final Set<String> BLOCKED_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                    // Items / rewards / progression
                    "give item", "grant item", "receive item", "reward", "loot",
                    "gain exp", "level up", "tặng vật phẩm", "trao vật phẩm",
                    "nhận vật phẩm", "phần thưởng", "lên cấp", "tăng cấp", "nhận exp",
                    // Spawning / combat / party
                    "spawn entity", "start combat", "take damage", "join party",
                    "join the party", "triệu hồi", "bắt đầu combat", "chiến đấu",
                    "gia nhập party", "vào party", "gây sát thương",
                    // Exits / level changes
                    "open exit", "change level", "mở lối thoát", "mở exit",
                    "đổi level", "chuyển level", "cửa thoát",
                    // Canon / organization claims
                    "the organization", "official", "tổ chức chính thức",
                    "đại diện chính thức",
                    // Quests / missions
                    "quest", "mission", "nhiệm vụ"
            )));

    // ------------------------------------------------------------------
    // Parsed value object
    // ------------------------------------------------------------------

    /** Validated ambient NPC payload. Immutable. */
    public static class AmbientNpc {
        /** 1-2 sentence appearance narration, max 500 chars. */
        public final String description;
        /** 2-4 dialogue lines, each non-empty, max 300 chars. */
        public final List<String> dialogues;
        /** Departure narration, max 500 chars. */
        public final String exit;

        public AmbientNpc(String description, List<String> dialogues, String exit) {
            this.description = description;
            this.dialogues = Collections.unmodifiableList(new ArrayList<>(dialogues));
            this.exit = exit;
        }
    }

    // ------------------------------------------------------------------
    // Static: prompt construction
    // ------------------------------------------------------------------

    /**
     * Builds the Gemini generation prompt.
     *
     * <p>Only two dynamic inputs are embedded: {@code levelId} (from Core) and
     * {@code sceneSnippet} (short current-scene description). Both are clipped
     * to safe lengths. The static portion instructs Gemini to invent a random
     * passerby person, small group, or passerby activity with no storyline, and
     * forbids Canon claims, official organizations, exits, items, and any
     * Core-manipulating instructions.
     */
    public static String buildPrompt(String levelId, String sceneSnippet) {
        String level = clip(safe(levelId), MAX_LEVEL_ID_LEN);
        String scene = clip(safe(sceneSnippet), MAX_SCENE_SNIPPET_LEN);

        return "Bạn là công cụ tạo NPC quần chúng cho game Backrooms.\n"
                + "\n"
                + "NGỮ CẢNH:\n"
                + "- Level hiện tại: " + level + "\n"
                + "- Bối cảnh: " + scene + "\n"
                + "\n"
                + "NHIỆM VỤ:\n"
                + "Tạo MỘT người qua đường ngẫu nhiên, một nhóm nhỏ, hoặc một hoạt động "
                + "đời thường của người qua đường tại bối cảnh trên. Không cần cốt truyện, "
                + "không cần tên riêng có ý nghĩa. Được phép sáng tạo ngoại hình, hành vi "
                + "và câu nói kỳ quặc, vô nghĩa hoặc đời thường.\n"
                + "\n"
                + "TUYỆT ĐỐI KHÔNG ĐƯỢC:\n"
                + "- Khẳng định bất kỳ thông tin Canon nào (tên nhân vật, sự kiện, lịch sử).\n"
                + "- Biến nhóm người thành đại diện chính thức của bất kỳ tổ chức nào. "
                + "Nếu không chắc một tổ chức tồn tại ở đây, thì không nhắc đến.\n"
                + "- Đề cập lối thoát, vật phẩm, phần thưởng, EXP, nhiệm vụ.\n"
                + "- Ra lệnh hoặc gợi ý thay đổi game: tăng cấp, triệu hồi, chiến đấu, "
                + "gia nhập party, mở lối thoát, chuyển level.\n"
                + "\n"
                + "ĐỊNH DẠNG TRẢ VỀ — JSON thuần (không markdown fence), đúng shape:\n"
                + "{\"description\": \"...\", \"dialogues\": [\"...\", \"...\"], \"exit\": \"...\"}\n"
                + "- \"description\": 1-2 câu mô tả cảnh NPC xuất hiện (tối đa 500 ký tự).\n"
                + "- \"dialogues\": 2 đến 4 câu thoại ngắn của NPC (mỗi câu tối đa 300 ký tự).\n"
                + "- \"exit\": 1 câu mô tả NPC rời khỏi cảnh (tối đa 500 ký tự).";
    }

    // ------------------------------------------------------------------
    // Static: parse + validate
    // ------------------------------------------------------------------

    /**
     * Parses and validates a Gemini JSON response.
     *
     * <p>Valid payload requires: non-empty {@code description} (<=500 chars),
     * {@code dialogues} with 2-4 non-empty items (each <=300 chars), non-empty
     * {@code exit} (<=500 chars), and no Core-manipulation keywords anywhere in
     * the text. Returns {@code null} for any invalid input. Never throws.
     */
    public static AmbientNpc parse(String json) {
        try {
            if (json == null) return null;
            String cleaned = stripCodeFence(json).trim();
            if (cleaned.isEmpty()) return null;

            JSONObject root = new JSONObject(cleaned);

            String description = root.optString("description", "").trim();
            if (description.isEmpty() || description.length() > MAX_DESCRIPTION_LEN) return null;

            JSONArray arr = root.optJSONArray("dialogues");
            if (arr == null) return null;
            int n = arr.length();
            if (n < MIN_DIALOGUES || n > MAX_DIALOGUES) return null;
            List<String> dialogues = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String line = arr.optString(i, "").trim();
                if (line.isEmpty() || line.length() > MAX_DIALOGUE_LEN) return null;
                dialogues.add(line);
            }

            String exit = root.optString("exit", "").trim();
            if (exit.isEmpty() || exit.length() > MAX_EXIT_LEN) return null;

            // Reject Core-manipulation / gameplay-claim content.
            StringBuilder all = new StringBuilder(description.length() + exit.length() + 64);
            all.append(description).append('\n');
            for (String d : dialogues) all.append(d).append('\n');
            all.append(exit);
            String lowered = all.toString().toLowerCase(Locale.ROOT);
            for (String blocked : BLOCKED_KEYWORDS) {
                if (lowered.contains(blocked)) return null;
            }

            return new AmbientNpc(description, dialogues, exit);
        } catch (Exception e) {
            // org.json throws JSONException (a RuntimeException) on malformed input.
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Instance: RAM-only active encounter cache (max one NPC at a time)
    // ------------------------------------------------------------------

    private AmbientNpc active;
    private int dialogueIndex;

    /** Activates an encounter, resetting the dialogue cycle to Phase 1. Null clears. */
    public synchronized void setActive(AmbientNpc npc) {
        this.active = npc;
        this.dialogueIndex = 0;
    }

    /** True while an ambient encounter is in progress. */
    public synchronized boolean hasActive() {
        return active != null;
    }

    /**
     * Returns the next dialogue line, cycling back to the first after the last
     * (Phase 1 → 2 → 3 → 4 → 1...). Returns {@code null} when no encounter is active.
     * Purely local — never calls any API.
     */
    public synchronized String nextDialogue() {
        if (active == null || active.dialogues.isEmpty()) return null;
        String line = active.dialogues.get(Math.floorMod(dialogueIndex, active.dialogues.size()));
        dialogueIndex++;
        return line;
    }

    /** Returns the appearance description of the active NPC, or {@code null}. */
    public synchronized String getDescription() {
        return active == null ? null : active.description;
    }

    /**
     * Ends the encounter: returns the cached departure narration and clears the
     * cache permanently. Returns {@code null} when no encounter is active.
     */
    public synchronized String endEncounter() {
        if (active == null) return null;
        String exitText = active.exit;
        active = null;
        dialogueIndex = 0;
        return exitText;
    }

    /** Clears the active encounter without returning anything. */
    public synchronized void clear() {
        active = null;
        dialogueIndex = 0;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    /** Removes surrounding ``` / ```json fences if present. */
    private static String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNl >= 0 && lastFence > firstNl) {
                t = t.substring(firstNl + 1, lastFence).trim();
            } else {
                t = t.replace("```json", "").replace("```", "").trim();
            }
        }
        return t;
    }
}
