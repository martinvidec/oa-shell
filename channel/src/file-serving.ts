import fs from 'node:fs';
import path from 'node:path';

export interface TreeEntry {
  name: string;
  type: 'file' | 'dir';
  size: number | null;
}

export type ContentResult =
  | { content: string; size: number }
  | { truncated: true; size: number }
  | { binary: true; size: number };

export class FileServingError extends Error {
  constructor(public readonly code: string, message: string) {
    super(message);
    this.name = 'FileServingError';
  }
}

export interface FileServer {
  tree(rel: string): TreeEntry[];
  content(rel: string): ContentResult;
}

const MAX_BYTES = 200_000;

function isBinary(buf: Buffer): boolean {
  const len = Math.min(buf.length, 8000);
  for (let i = 0; i < len; i++) {
    if (buf[i] === 0) {
      return true; // Null-Byte -> als binär behandeln
    }
  }
  return false;
}

/**
 * File-Serving STRIKT begrenzt auf {@code baseDir} (= cwd der Session). Pfade werden
 * kanonisiert (realpath); {@code ..}-Ausbrüche und Symlinks, die aus dem Verzeichnis
 * herausführen, werden abgewiesen. Eingehende absolute Pfade werden relativ zu
 * {@code baseDir} interpretiert (führende Slashes entfernt) und können es nicht verlassen.
 */
export function createFileServer(baseDir: string): FileServer {
  const base = fs.realpathSync(baseDir);

  function safeResolve(rel: string): string {
    const cleaned = rel && rel !== '/' ? rel.replace(/^\/+/, '') : '.';
    const target = path.resolve(base, cleaned);
    let real: string;
    try {
      real = fs.realpathSync(target);
    } catch {
      throw new FileServingError('not_found', 'Pfad nicht gefunden');
    }
    if (real !== base && !real.startsWith(base + path.sep)) {
      throw new FileServingError('outside_cwd', 'Pfad außerhalb des Arbeitsverzeichnisses');
    }
    return real;
  }

  function tree(rel: string): TreeEntry[] {
    const dir = safeResolve(rel);
    if (!fs.statSync(dir).isDirectory()) {
      throw new FileServingError('not_a_dir', 'Kein Verzeichnis');
    }
    return fs.readdirSync(dir, { withFileTypes: true }).map((d) => {
      let size: number | null = null;
      if (d.isFile()) {
        try {
          size = fs.statSync(path.join(dir, d.name)).size;
        } catch {
          size = null;
        }
      }
      return { name: d.name, type: d.isDirectory() ? 'dir' : 'file', size };
    });
  }

  function content(rel: string): ContentResult {
    const file = safeResolve(rel);
    const st = fs.statSync(file);
    if (!st.isFile()) {
      throw new FileServingError('not_a_file', 'Keine Datei');
    }
    if (st.size > MAX_BYTES) {
      return { truncated: true, size: st.size };
    }
    const buf = fs.readFileSync(file);
    if (isBinary(buf)) {
      return { binary: true, size: st.size };
    }
    return { content: buf.toString('utf8'), size: st.size };
  }

  return { tree, content };
}
