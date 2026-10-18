import { authRoute, session } from './auth.js';
import { mutate, changes, documentObject, finalizeDocument } from './records.js';
import { HttpError, json, rateLimit } from './http.js';
import { storageSummary, optimizeDocument } from './storage.js';

export default {
  async fetch(request, bindings) {
    const path = new URL(request.url).pathname;
    try {
      if (path === '/health' && request.method === 'GET')
        return json({ service: 'tickettracker', api_version: 1, storage: 'D1+R2' });
      // Every request starts on the primary; no stale-replica decisions for sessions, CAS or feeds.
      const env = { ...bindings, DB: bindings.DB.withSession('first-primary') };
      if (path.startsWith('/v1/auth/')) return await authRoute(request, env, path);
      const owner = await session(request, env);
      await rateLimit(env, 'project-data-day', 5000, 86400);
      await rateLimit(env, `user:${owner.id}`, 2000, 86400);
      if (path === '/v1/me' && request.method === 'GET')
        return json({ id: owner.id, email: owner.email });
      if (path === '/v1/storage' && request.method === 'GET')
        return await storageSummary(env, owner);
      const optimize = path.match(/^\/v1\/documents\/([^/]+)\/optimize$/);
      if (optimize && request.method === 'POST')
        return await optimizeDocument(env, owner, optimize[1]);
      const record = path.match(/^\/v1\/records\/([0-9a-f-]{36})$/i);
      if (record && request.method === 'GET') {
        const row = await env.DB.prepare(
          'SELECT id,kind,revision,body,deleted_at FROM tt_records WHERE id=? AND owner_id=?'
        )
          .bind(record[1], owner.id)
          .first();
        if (!row) throw new HttpError(404, 'not_found');
        return json({ ...row, body: JSON.parse(row.body) });
      }
      if (path === '/v1/sync/mutate' && request.method === 'POST')
        return await mutate(request, env, owner);
      if (path === '/v1/sync/changes' && request.method === 'GET')
        return await changes(request, env, owner);
      const object = path.match(/^\/v1\/documents\/([^/]+)\/variants\/([^/]+)$/);
      if (object) return await documentObject(request, env, owner, object[1], object[2]);
      const finalize = path.match(/^\/v1\/documents\/([^/]+)\/finalize$/);
      if (finalize && request.method === 'POST')
        return await finalizeDocument(env, owner, finalize[1]);
      throw new HttpError(404, 'not_found');
    } catch (error) {
      return json(
        { error: error instanceof HttpError ? error.code : 'service_unavailable' },
        error instanceof HttpError ? error.status : 503
      );
    }
  },
  async scheduled(_event, env) {
    const now = Math.floor(Date.now() / 1000);
    await env.DB.batch([
      env.DB.prepare('DELETE FROM tt_email_challenges WHERE expires_at<?').bind(now - 86400),
      env.DB.prepare('DELETE FROM tt_google_challenges WHERE expires_at<?').bind(now - 86400),
      env.DB.prepare('DELETE FROM tt_sessions WHERE expires_at<?').bind(now),
      env.DB.prepare('DELETE FROM tt_rate_limits WHERE resets_at<?').bind(now - 86400),
    ]);
  },
};
