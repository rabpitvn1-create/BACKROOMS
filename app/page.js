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

  useEffect(() => {
    load().catch(() => {});
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
      setStatus(
        result.saved
          ? `Đã xử lý lượt chơi và chuyển sang Turn ${result.state.turn}. State đã lưu trên ${result.storage}.`
          : "Lượt chưa được xác nhận lưu.",
      );
    } catch (error) {
      setStatus(`Lượt không được lưu: ${error.message}`);
      await load("Đã khôi phục state server sau lỗi.").catch(() => {});
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
      setStatus(`New Game đã sẵn sàng ở Turn 1. State lưu trên ${result.storage}.`);
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
      setStatus(`Đã nhập state và lưu trên ${result.storage}.`);
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
            <h1>{state.title || "Backrooms Session"}</h1>
          </div>
          <div className="turn">TURN <strong>{state.turn ?? 1}</strong></div>
        </header>

        <div className="snapshot">
          {state.snapshotUrl ? (
            <img src={state.snapshotUrl} alt="Ảnh trạng thái hiện tại" />
          ) : (
            <div className="snapshot-empty"><span>NO SNAPSHOT</span><small>Ảnh hiện chưa được tạo.</small></div>
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
            placeholder="Kai làm gì trong Turn hiện tại?"
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
            <div><dt>Chế độ</dt><dd>{state.mode || "—"}</dd></div>
            <div><dt>Canon</dt><dd>{state.canonLoaded ? "Đã nạp" : "Chưa nạp"}</dd></div>
            <div><dt>Nhân vật</dt><dd>{state.player?.name || "Chưa xác định"}</dd></div>
          </dl>
        </div>

        <div className="card">
          <h2>Save / Load</h2>
          <div className="actions">
            <button onClick={save} disabled={busy}>Lưu</button>
            <button onClick={() => load()} disabled={busy}>Tải</button>
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
      </aside>
    </main>
  );
}
