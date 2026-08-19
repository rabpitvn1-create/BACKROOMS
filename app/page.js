"use client";

import { useEffect, useRef, useState } from "react";

const placeholder = {
  title: "Backrooms Session",
  turn: 0,
  location: "Đang nạp canon…",
  mode: "backend",
  canonLoaded: false,
  player: { name: "Đang tải…", condition: "" },
  party: [],
  inventory: [],
  log: [{ role: "gm", text: "Đang tải trạng thái từ server…" }],
  snapshotUrl: null
};

async function readJson(response) {
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data?.error || `HTTP ${response.status}`);
    error.status = response.status;
    error.data = data;
    throw error;
  }
  return data;
}

function itemName(item) {
  if (typeof item === "string") return item;
  if (item && typeof item.name === "string") return item.name;
  return "Vật phẩm";
}

function partyName(member) {
  if (typeof member === "string") return member;
  if (member && typeof member.name === "string") return member.name;
  return "Thành viên";
}

export default function GamePage() {
  const [state, setState] = useState(placeholder);
  const [storage, setStorage] = useState("đang tải");
  const [status, setStatus] = useState("Đang tải save từ server…");
  const [action, setAction] = useState("");
  const [busy, setBusy] = useState(true);
  const fileRef = useRef(null);
  const logRef = useRef(null);

  const load = async (label = "Đã tải state mới nhất từ server.") => {
    setBusy(true);
    try {
      const data = await readJson(await fetch("/api/game-state", { cache: "no-store" }));
      setState(data.state);
      setStorage(data.storage || "server");
      setStatus(label);
    } catch (error) {
      setStatus(`Lỗi tải state: ${error.message}`);
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    load("Canon và save đã được nạp từ server.");
  }, []);

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [state?.log?.length]);

  const save = async () => {
    setBusy(true);
    setStatus("Đang lưu lên server…");
    try {
      const data = await readJson(await fetch("/api/game-state", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ state, expectedRevision: state.revision })
      }));
      setState(data.state);
      setStorage(data.storage || storage);
      setStatus("Đã lưu trên server.");
    } catch (error) {
      if (error.status === 409 && error.data?.state) setState(error.data.state);
      setStatus(`Lưu thất bại: ${error.message}`);
    } finally {
      setBusy(false);
    }
  };

  const reset = async () => {
    if (!window.confirm("Reset session này về CURRENT CANON Turn 9?")) return;
    setBusy(true);
    setStatus("Đang reset state trên server…");
    try {
      const data = await readJson(await fetch("/api/game-state", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reset: true })
      }));
      setState(data.state);
      setStorage(data.storage || storage);
      setStatus("Đã reset session về CURRENT CANON Turn 9 trên server.");
    } catch (error) {
      setStatus(`Reset thất bại: ${error.message}`);
    } finally {
      setBusy(false);
    }
  };

  const submit = async (event) => {
    event.preventDefault();
    const text = action.trim();
    if (!text || busy) return;
    setBusy(true);
    setAction("");
    setStatus("Gemini đang xử lý lượt…");
    try {
      const data = await readJson(await fetch("/api/game-turn", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: text })
      }));
      setState(data.state);
      setStorage(data.storage || storage);
      setStatus(data.saved ? `Turn ${data.state.turn} đã xử lý và lưu trên server.` : "Lượt chưa được lưu.");
    } catch (error) {
      if (error.data?.state) setState(error.data.state);
      setStatus(`Lượt không được lưu: ${error.message}`);
    } finally {
      setBusy(false);
    }
  };

  const exportJson = () => {
    const blob = new Blob([JSON.stringify(state, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `backroom-${state.sessionId || "session"}-turn-${state.turn}.json`;
    a.click();
    URL.revokeObjectURL(url);
    setStatus("Đã xuất bản sao JSON cục bộ. Server vẫn là source of truth.");
  };

  const importJson = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setBusy(true);
    try {
      const imported = JSON.parse(await file.text());
      const data = await readJson(await fetch("/api/game-state", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ state: imported, expectedRevision: state.revision })
      }));
      setState(data.state);
      setStorage(data.storage || storage);
      setStatus("JSON đã được validate và lưu lên server.");
    } catch (error) {
      if (error.data?.state) setState(error.data.state);
      setStatus(`Nhập JSON thất bại: ${error.message}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="shell">
      <section className="game">
        <header className="topbar">
          <div>
            <div className="eyebrow">BACKROOM TEXT GAME</div>
            <h1>{state.title || "Backrooms Session"}</h1>
          </div>
          <div className="turn">TURN <strong>{state.turn ?? 0}</strong></div>
        </header>

        <div className="snapshot">
          {state.snapshotUrl ? (
            <img src={state.snapshotUrl} alt="Ảnh snapshot của trạng thái game" />
          ) : (
            <div className="snapshot-empty"><span>NO SNAPSHOT</span><small>Ảnh hiện chưa được tạo.</small></div>
          )}
        </div>

        <div className="log" ref={logRef} aria-live="polite">
          {(state.log || []).map((entry, index) => (
            <article className={`message ${entry.role === "player" ? "player" : "gm"}`} key={`${index}-${entry.text?.slice(0, 20)}`}>
              <div className="role">{entry.role === "player" ? "KAI / PLAYER" : "GAME MASTER"}</div>
              <div className="text">{entry.text}</div>
            </article>
          ))}
        </div>

        <form className="composer" onSubmit={submit}>
          <textarea
            value={action}
            onChange={(event) => setAction(event.target.value)}
            disabled={busy}
            placeholder="Kai làm gì tiếp theo?"
            rows={3}
            maxLength={4000}
          />
          <button type="submit" disabled={busy || !action.trim()}>{busy ? "ĐANG XỬ LÝ…" : "HÀNH ĐỘNG"}</button>
        </form>
        <div className="status">{status} <span className="storage">storage: {storage}</span></div>
      </section>

      <aside className="side">
        <div className="card">
          <h2>Trạng thái</h2>
          <dl>
            <div><dt>Vị trí</dt><dd>{state.location}</dd></div>
            <div><dt>Chế độ</dt><dd>{state.mode}</dd></div>
            <div><dt>Canon</dt><dd>{state.canonLoaded ? "Đã nạp" : "Chưa nạp"}</dd></div>
            <div><dt>Nhân vật</dt><dd>{state.player?.name || "Chưa xác định"}</dd></div>
            <div><dt>Tình trạng</dt><dd>{state.player?.condition || "Không rõ"}</dd></div>
          </dl>
        </div>

        <div className="card">
          <h2>Save / Load</h2>
          <div className="actions">
            <button disabled={busy} onClick={save}>Lưu</button>
            <button disabled={busy} onClick={() => load()}>Tải</button>
            <button disabled={busy} onClick={exportJson}>Xuất JSON</button>
            <button disabled={busy} onClick={() => fileRef.current?.click()}>Nhập JSON</button>
            <input ref={fileRef} type="file" accept="application/json,.json" hidden onChange={importJson} />
            <button className="danger" disabled={busy} onClick={reset}>Reset trạng thái</button>
          </div>
        </div>

        <div className="card">
          <h2>Party</h2>
          <div className="chips">
            {(state.party || []).length ? state.party.map((member, index) => <span key={index}>{partyName(member)}</span>) : <em>Hiện Kai đang tách khỏi Iris và Syvial.</em>}
          </div>
        </div>

        <div className="card">
          <h2>Inventory</h2>
          <div className="chips">
            {(state.inventory || []).length ? state.inventory.map((item, index) => <span key={index}>{itemName(item)}</span>) : <em>Trống.</em>}
          </div>
        </div>
      </aside>
    </main>
  );
}
