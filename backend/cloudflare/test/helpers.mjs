import { readFile } from 'node:fs/promises';

export async function applySchema(db) {
  const sql = await readFile(new URL('../migrations/0001_initial.sql', import.meta.url), 'utf8');
  let statement = '';
  let trigger = false;
  for (const line of sql.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('--')) continue;
    if (trimmed.startsWith('CREATE TRIGGER')) trigger = true;
    statement += `${line}\n`;
    if ((trigger && trimmed === 'END;') || (!trigger && trimmed.endsWith(';'))) {
      await db.prepare(statement).run();
      statement = '';
      trigger = false;
    }
  }
}
