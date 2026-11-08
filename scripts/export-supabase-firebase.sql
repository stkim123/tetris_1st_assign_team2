-- Run using a privileged PostgreSQL connection, AFTER draining ranked admission.
-- psql "$env:SUPABASE_DB_URL" -X -q -A -t -v ON_ERROR_STOP=1 -f scripts/export-supabase-firebase.sql > .migration-private/supabase-export.json
-- Output contains password hashes. Never paste it into chat, commit, or upload to a build.
begin isolation level repeatable read read only;
do $$ begin
  if exists (select 1 from public.matches where status = 'RUNNING') then
    raise exception 'Drain and settle all running matches before exporting';
  end if;
  if exists (select 1 from public.ranked_admission_control where enabled) then
    raise exception 'Ranked admission must be disabled before exporting';
  end if;
end $$;
select json_build_object(
 'schemaVersion', 1, 'exportedAt', now(),
 'users', (select coalesce(json_agg(json_build_object(
   'id', id, 'email', email, 'encrypted_password', encrypted_password,
   'email_confirmed_at', email_confirmed_at, 'banned_until', banned_until,
   'raw_user_meta_data', raw_user_meta_data)), '[]'::json) from auth.users),
 'profiles', (select coalesce(json_agg(t), '[]'::json) from public.profiles t),
 'player_stats', (select coalesce(json_agg(t), '[]'::json) from public.player_stats t),
 'matches', (select coalesce(json_agg(t), '[]'::json) from public.matches t),
 'match_participants', (select coalesce(json_agg(t), '[]'::json) from public.match_participants t),
 'server_runs', (select coalesce(json_agg(t), '[]'::json) from public.server_runs t)
);
commit;
