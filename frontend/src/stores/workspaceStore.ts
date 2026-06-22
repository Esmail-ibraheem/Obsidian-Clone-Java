import { create } from "zustand";

/** A single editor pane holding a set of open tabs (note paths). */
export interface Pane {
  id: string;
  tabs: string[];
  active: string | null;
}

interface WorkspaceState {
  panes: Pane[];
  activePaneId: string;
  /** Open a note. With {split:true}, open it in a new pane beside the active one. */
  openTab: (path: string, opts?: { split?: boolean }) => void;
  closeTab: (paneId: string, path: string) => void;
  setActiveTab: (paneId: string, path: string) => void;
  setActivePane: (paneId: string) => void;
  /** Split the active pane, duplicating its active tab into a new pane. */
  splitActive: () => void;
}

let paneCounter = 0;
function newPaneId(): string {
  paneCounter += 1;
  return `pane-${paneCounter}`;
}

function createPane(tabs: string[] = [], active: string | null = null): Pane {
  return { id: newPaneId(), tabs, active };
}

const initialPane = createPane();

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  panes: [initialPane],
  activePaneId: initialPane.id,

  openTab(path, opts) {
    set((state) => {
      if (opts?.split) {
        const newPane = createPane([path], path);
        return { panes: [...state.panes, newPane], activePaneId: newPane.id };
      }
      const panes = state.panes.map((pane) => {
        if (pane.id !== state.activePaneId) return pane;
        const tabs = pane.tabs.includes(path) ? pane.tabs : [...pane.tabs, path];
        return { ...pane, tabs, active: path };
      });
      return { panes };
    });
  },

  closeTab(paneId, path) {
    set((state) => {
      let panes = state.panes.map((pane) => {
        if (pane.id !== paneId) return pane;
        const tabs = pane.tabs.filter((t) => t !== path);
        const active = pane.active === path ? (tabs[tabs.length - 1] ?? null) : pane.active;
        return { ...pane, tabs, active };
      });
      // Drop empty panes, but always keep at least one.
      const nonEmpty = panes.filter((p) => p.tabs.length > 0);
      panes = nonEmpty.length > 0 ? nonEmpty : [createPane()];
      const activePaneId = panes.some((p) => p.id === state.activePaneId)
        ? state.activePaneId
        : panes[0].id;
      return { panes, activePaneId };
    });
  },

  setActiveTab(paneId, path) {
    set((state) => ({
      activePaneId: paneId,
      panes: state.panes.map((pane) =>
        pane.id === paneId ? { ...pane, active: path } : pane,
      ),
    }));
  },

  setActivePane(paneId) {
    set({ activePaneId: paneId });
  },

  splitActive() {
    set((state) => {
      const active = state.panes.find((p) => p.id === state.activePaneId);
      const path = active?.active ?? null;
      const newPane = createPane(path ? [path] : [], path);
      return { panes: [...state.panes, newPane], activePaneId: newPane.id };
    });
  },
}));

/** The path shown in the active pane, or null. Used to drive backlinks/title. */
export function activePath(state: WorkspaceState): string | null {
  return state.panes.find((p) => p.id === state.activePaneId)?.active ?? null;
}
