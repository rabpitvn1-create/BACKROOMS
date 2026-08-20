"use client";

import { useEffect, useRef, useState } from "react";

const INITIAL_STATE = {
  version: 1,
  title: "Backrooms Session",
  turn: 1,
  mode: "backend",
  canonLoaded: false,
  location: "Đang nạp New Game…",
  player: { name: "Kai Akechi", hp: null, condition: "Bình thường" },
  party: [],
  inventory: [],
  flags: {},
  snapshotUrl: null,
  log: [{ role: "gm", text: "Đang tải Prologue và trạng thái New Game từ server…" }],
  updatedAt: null,
};

function levelHeader(state) {
  const explicitNumber = state?.level?.number ?? state?.flags?.currentLevel?.number;
  const explicitName = state?.level?.name ?? state?.flags?.currentLevel?.name;
  if (explicitNumber != null && explicitName) return `Level ${explicitNumber} – ${explicitName}`;

  const location = typeof state?.location === "string" ? state.location : "";
  const match = location.match(/\bLevel\s+([^\s/—–-]+)\s*(?:\/|—|–|-)\s*([^—–\n]+?)(?:\s+[—–]\s+|$)/i);
  if (match) return `Level ${match[1]} – ${match[2].trim()}`;

  return state?.title || "Backrooms Session";
}

function rollLabel(name, roll) {
  if (!roll || typeof roll !== "object") return null;
  if (!roll.eligible) return `${name}: INELIGIBLE`;
  if (roll.guaranteedByState) return `${name}: ĐỦ ĐIỀU KIỆN CANON`;
  return `${name}: ${roll.raw}/${roll.threshold}${roll.success ? " — SUCCESS" : ""}`;
}

async function readJson(response) {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body?.error || `HTTP ${response.status}`);
  return body;
}

export default function Home() {
  const [state, setState] = useState(INITIAL_STATE);
  const [action, setAction] = useState("");
  const [busy, setBusy] = useState(true);
  const [status, setStatus] = useState("Đang tải save từ server…");
  const fileRef = useRef(null);

  async function load(message = "Đã tải state mới nhất từ server.") {
    setBusy(true);
    try {
      const result = await readJson(await fetch("/api/game-state", { cache: "no-store" }));
      setState(result.state);
      setStatus(`${message} Storage: ${result.storage}.`);
      return result.state;
    } catch (error) {
      setStatus(`Lỗi tải: ${error.message}`);
      throw error;
    } finally {
      setBusy(false);
    }
  }

  async function requestSnapshot(label = "Đang tạo snapshot bằng Gemini…") {
    setStatus(label);
    const result = await readJson(
      await fetch("/api/snapshot", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: "{}",
      }),
    );
    setState(result.state);
    setStatus(`Snapshot Turn ${result.state.turn} đã tạo bằng ${result.imageModel}.`);
    return result.state;
  }

  useEffect(() => {
    (async () => {
      try {
        await load("Đã tải New Game từ server.");
      } catch {
        // load() đã hiển thị lỗi.
      }
    })();
  }, []);

  async function submit(event) {
    event?.preventDefault();
    const text = action.trim();
    if (!text || busy) return;

    setBusy(true);
    setStatus("Gemini đang xử lý lượt…");
    try {
      const result = await readJson(
        await fetch("/api/game-turn", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: text }),
        }),
      );
      setState(result.state);
      setAction("");

      if (result.saved && result.snapshotRequested) {
        setStatus(`Turn ${result.state.turn} có sự kiện đặc biệt. Đang tạo snapshot…`);
        try {
          await requestSnapshot(`Gemini đang dựng snapshot cho sự kiện đặc biệt ở Turn ${result.state.turn}…`);
        } catch (snapshotError) {
          setStatus(`Turn ${result.state.turn} đã lưu, nhưng snapshot sự kiện bị lỗi: ${snapshotError.message}`);
        }
      } else if (result.saved && result.turnAdvanced === false) {
        setStatus(`Đã trả lời từ state/canon hiện tại; Turn không tăng. Storage: ${result.storage}.`);
      } else if (result.saved) {
        setStatus(`Turn ${result.state.turn} đã lưu trên ${result.storage}. Snapshot cũ được giữ nguyên.`);
      } else {
        setStatus("Lượt chưa được xác nhận lưu.");
      }
    } catch (error) {
      setStatus(`Lượt không được lưu: ${error.message}`);
      await load("Đã khôi phục state server sau lỗi.").catch(() => {});
    } finally {
      setBusy(false);
    }
  }

  async function manualSnapshot() {
    if (busy) return;
    setBusy(true);
    try {
      await requestSnapshot();
    } catch (error) {
      setStatus(`Tạo snapshot thất bại: ${error.message}`);
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    if (busy) return;
    setBusy(true);
    setStatus("Đang lưu lên server…");
    try {
      const result = await readJson(
        await fetch("/api/game-state", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ state, expectedRevision: state.revision }),
        }),
      );
      setState(result.state);
      setStatus(`Đã lưu trên ${result.storage}.`);
    } catch (error) {
      setStatus(`Lưu thất bại: ${error.message}`);
    } finally {
      setBusy(false);
    }
  }

  async function reset() {
    if (busy || !window.confirm("Bắt đầu NEW GAME từ Prologue? Save hiện tại của session này sẽ được thay bằng trạng thái Turn 1 mới.")) return;
    setBusy(true);
    setStatus("Đang tạo New Game từ Prologue…");
    try {
      const result = await readJson(
        await fetch("/api/game-state", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ reset: true }),
        }),
      );
      setState(result.state);
      setAction("");
      setStatus(`New Game đã sẵn sàng ở Turn 1 trên ${result.storage}. Snapshot chỉ tạo khi có sự kiện đặc biệt hoặc khi bạn bấm tạo thủ công.`);
    } catch (error) {
      setStatus(`Tạo New Game thất bại: ${error.message}`);
    } finally {
      setBusy(false);
    }
  }

  async function importState(file) {
    if (!file || busy) return;
    setBusy(true);
    setStatus("Đang nhập và lưu state lên server…");
    try {
      const imported = JSON.parse(await file.text());
      const result = await readJson(
        await fetch("/api/game-state", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ state: imported, expectedRevision: state.revision }),
        }),
      );
      setState(result.state);
      setStatus(`Đã nhập state trên ${result.storage}. Snapshot hiện có được giữ nguyên.`);
    } catch (error) {
      setStatus(`Nhập thất bại: ${error.message}`);
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  function exportState() {
    const blob = new Blob([JSON.stringify(state, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `backroom-turn-${state.turn}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <main className="shell">
      <section className="game">
        <header className="topbar">
          <div>
            <div className="eyebrow">BACKROOM TEXT GAME</div>
            <h1>{levelHeader(state)}</h1>
          </div>
          <div className="turn">TURN <strong>{state.turn ?? 1}</strong></div>
        </header>

        <div className="snapshot">
          {state.snapshotUrl ? (
            <img key={state.snapshotUrl} src={state.snapshotUrl} alt={`Snapshot Turn ${state.turn}`} />
          ) : (
            <div className="snapshot-empty"><span>GEMINI SNAPSHOT</span><small>{busy ? "Đang dựng ảnh hiện tại…" : "Chưa có ảnh. Có thể tạo snapshot thủ công."}</small></div>
          )}
        </div>

        <div className="log" aria-live="polite">
          {(state.log || []).map((item, index) => (
            <article className={`message ${item.role || "gm"}`} key={`${index}-${item.text?.slice(0, 20)}`}>
              <div className="role">
                {item.role === "player" ? "BẠN" : item.role === "gm" ? "GAME MASTER" : String(item.role || "SYSTEM").toUpperCase()}
              </div>
              <div className="text">{item.text}</div>
            </article>
          ))}
        </div>

        <form className="composer" onSubmit={submit}>
          <textarea
            value={action}
            onChange={(event) => setAction(event.target.value)}
            disabled={busy}
            placeholder="Kai sẽ làm gì tiếp theo?"
            rows={3}
          />
          <button type="submit" disabled={busy || !action.trim()}>{busy ? "ĐANG XỬ LÝ…" : "THỰC HIỆN"}</button>
        </form>
        <div className="status">{status}</div>
      </section>

      <aside className="side">
        <div className="card">
          <h2>Trạng thái</h2>
          <dl>
            <div><dt>Vị trí</dt><dd>{state.location || "—"}</dd></div>
            <div><dt>Mục tiêu</dt><dd>{state.flags?.currentObjective || "—"}</dd></div>
            <div><dt>Chế độ</dt><dd>{state.mode || "—"}</dd></div>
            <div><dt>Canon</dt><dd>{state.canonLoaded ? "Đã nạp" : "Chưa nạp"}</dd></div>
            <div><dt>Nhân vật</dt><dd>{state.player?.name || "Chưa xác định"}</dd></div>
            <div><dt>Tình trạng</dt><dd>{state.player?.condition || "—"}</dd></div>
          </dl>
        </div>

        <div className="card">
          <h2>Nhu cầu & liên lạc</h2>
          <dl>
            {Object.entries(state.player?.needs || {}).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{String(value)}</dd></div>)}
            {Object.entries(state.flags?.communication || {}).map(([key, value]) => <div key={`link-${key}`}><dt>{key}</dt><dd>{String(value)}</dd></div>)}
          </dl>
        </div>

        <div className="card">
          <h2>Save / Load</h2>
          <div className="actions">
            <button onClick={save} disabled={busy}>Lưu</button>
            <button onClick={() => load()} disabled={busy}>Tải</button>
            <button onClick={manualSnapshot} disabled={busy}>Tạo Snapshot</button>
            <button onClick={exportState} disabled={busy}>Xuất JSON</button>
            <button onClick={() => fileRef.current?.click()} disabled={busy}>Nhập JSON</button>
            <input ref={fileRef} type="file" accept="application/json,.json" hidden onChange={(event) => importState(event.target.files?.[0])} />
            <button className="danger" onClick={reset} disabled={busy}>Bắt đầu lại từ đầu</button>
          </div>
        </div>

        <div className="card">
          <h2>Party</h2>
          <div className="chips">
            {state.party?.length ? state.party.map((member, index) => <span key={index}>{typeof member === "string" ? member : member?.name || `#${index + 1}`}</span>) : <em>Hiện Kai đang một mình.</em>}
          </div>
        </div>

        <div className="card">
          <h2>Inventory</h2>
          <div className="chips">
            {state.inventory?.length ? state.inventory.map((item, index) => <span key={index}>{typeof item === "string" ? item : item?.name || `#${index + 1}`}</span>) : <em>Trống.</em>}
          </div>
        </div>

        <div className="card">
          <h2>Omnivault</h2>
          <div className="chips">
            {["slot1", "slot2", "slot3"].map((slot) => <span key={slot}>{slot.toUpperCase()}: {String(state.flags?.omnivault?.[slot] || "EMPTY")}</span>)}
          </div>
        </div>

        <div className="card">
          <h2>Manh mối & tuyến mở</h2>
          <ul className="compact-list">
            {(state.flags?.exploration?.clues || []).map((clue, index) => <li key={`clue-${index}`}>{String(clue)}</li>)}
            {(state.flags?.openThreads || []).map((thread, index) => <li key={`thread-${index}`}>{String(thread)}</li>)}
          </ul>
        </div>

        {state.flags?.lastRolls && (
          <div className="card">
            <h2>Dice gần nhất</h2>
            <div className="rolls">
              {Object.entries(state.flags.lastRolls)
                .filter(([key]) => key !== "turn")
                .map(([key, value]) => rollLabel(key, value))
                .filter(Boolean)
                .map((label) => <span key={label}>{label}</span>)}
            </div>
          </div>
        )}
      </aside>
    </main>
  );
}
