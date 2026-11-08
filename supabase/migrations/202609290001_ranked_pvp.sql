-- Authenticated 1:1 PvP only. Apply in Supabase SQL editor before enabling ranked rooms.
begin;
create table if not exists public.server_runs (
  run_id uuid primary key,
  state text not null check (state in ('ACTIVE', 'STOPPED')),
  created_at timestamptz not null default now(),
  stopped_at timestamptz,
  check ((state = 'ACTIVE' and stopped_at is null)
    or (state = 'STOPPED' and stopped_at is not null))
);

create table if not exists public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 32)
);

create table if not exists public.matches (
  match_id uuid primary key,
  server_run_id uuid not null,
  rules_version text not null check (char_length(rules_version) between 1 and 100),
  first_user_id uuid not null references auth.users(id),
  second_user_id uuid not null references auth.users(id),
  status text not null default 'RUNNING' check (status in ('RUNNING','FINALIZED','VOID')),
  winner_user_id uuid references auth.users(id),
  reason text,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  check (first_user_id <> second_user_id),
  check (winner_user_id is null or winner_user_id in (first_user_id, second_user_id)),
  check ((status = 'RUNNING' and ended_at is null and winner_user_id is null)
    or (status = 'VOID' and ended_at is not null and winner_user_id is null)
    or (status = 'FINALIZED' and ended_at is not null and winner_user_id is not null))
);

create table if not exists public.player_stats (
  user_id uuid primary key references auth.users(id) on delete cascade,
  rating integer not null default 1000,
  wins integer not null default 0,
  losses integer not null default 0,
  games integer not null default 0,
  active_match_id uuid references public.matches(match_id),
  check (wins >= 0 and losses >= 0 and games = wins + losses)
);

create table if not exists public.match_participants (
  match_id uuid not null references public.matches(match_id),
  user_id uuid not null references auth.users(id),
  slot smallint not null check (slot in (1,2)),
  outcome text check (outcome in ('WIN','LOSS','VOID')),
  rating_before integer,
  rating_after integer,
  primary key (match_id, user_id),
  unique (match_id, slot)
);

create index if not exists matches_run_status_idx on public.matches(server_run_id, status);
create index if not exists player_stats_leaderboard_idx on public.player_stats(rating desc, user_id) where games > 0;

-- Reapplication to an existing ranked database must retain a fence for older matches.
insert into public.server_runs(run_id, state)
select distinct server_run_id, 'ACTIVE' from public.matches on conflict do nothing;

create or replace function public.create_ranked_profile() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles(user_id, display_name)
  values (new.id, 'Player ' || left(new.id::text, 8)) on conflict do nothing;
  insert into public.player_stats(user_id) values (new.id) on conflict do nothing;
  return new;
end;
$$;

drop trigger if exists create_ranked_profile_on_auth_user on auth.users;
create trigger create_ranked_profile_on_auth_user after insert on auth.users
for each row execute function public.create_ranked_profile();

insert into public.profiles(user_id, display_name)
select id, 'Player ' || left(id::text, 8) from auth.users on conflict do nothing;
insert into public.player_stats(user_id)
select id from auth.users on conflict do nothing;

create or replace function public.ranked_match_record(p_match_id uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'match_id', m.match_id, 'server_run_id', m.server_run_id,
    'first_user_id', m.first_user_id, 'second_user_id', m.second_user_id,
    'status', m.status, 'winner_user_id', m.winner_user_id, 'reason', m.reason,
    'first_rating_before', p1.rating_before, 'first_rating_after', p1.rating_after,
    'second_rating_before', p2.rating_before, 'second_rating_after', p2.rating_after)
  from public.matches m
  join public.match_participants p1 on p1.match_id = m.match_id and p1.slot = 1
  join public.match_participants p2 on p2.match_id = m.match_id and p2.slot = 2
  where m.match_id = p_match_id;
$$;

create or replace function public.ranked_get_match(p_match_id uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select public.ranked_match_record(p_match_id);
$$;

create or replace function public.ranked_begin_match(
  p_match_id uuid, p_server_run uuid, p_rules_version text, p_first_user uuid, p_second_user uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_match public.matches%rowtype;
  v_stat public.player_stats%rowtype;
  v_run_state text;
  v_count integer := 0;
begin
  if p_first_user is null or p_second_user is null or p_first_user = p_second_user
      or p_match_id is null or p_server_run is null
      or char_length(coalesce(p_rules_version, '')) not between 1 and 100 then
    raise exception 'INVALID_MATCH' using errcode = 'PT400';
  end if;
  -- Run fencing precedes match/account locks. Recovery waits for this transaction.
  insert into public.server_runs(run_id, state) values (p_server_run, 'ACTIVE')
    on conflict do nothing;
  select state into v_run_state from public.server_runs where run_id = p_server_run for update;
  if v_run_state is distinct from 'ACTIVE' then
    raise exception 'RUN_STOPPED' using errcode = 'PT409'; end if;
  -- Duplicate starts serialize on match ID before touching account locks.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_match_id::text, 0));
  select * into v_match from public.matches where match_id = p_match_id for update;
  if found then
    if v_match.server_run_id <> p_server_run or v_match.rules_version <> p_rules_version
        or v_match.first_user_id <> p_first_user or v_match.second_user_id <> p_second_user then
      raise exception 'MATCH_ID_CONFLICT' using errcode = 'PT409';
    end if;
    return public.ranked_match_record(p_match_id);
  end if;
  -- Stable lock order serializes games sharing either account, including concurrent rematches.
  for v_stat in select * from public.player_stats
      where user_id in (p_first_user, p_second_user) order by user_id for update loop
    v_count := v_count + 1;
  end loop;
  if v_count <> 2 then raise exception 'UNKNOWN_ACCOUNT' using errcode = 'PT400'; end if;

  if exists (select 1 from public.player_stats where user_id in (p_first_user, p_second_user)
      and active_match_id is not null) then
    raise exception 'ACCOUNT_IN_ACTIVE_MATCH' using errcode = 'PT409';
  end if;
  insert into public.matches(match_id, server_run_id, rules_version, first_user_id, second_user_id)
  values (p_match_id, p_server_run, p_rules_version, p_first_user, p_second_user);
  insert into public.match_participants(match_id, user_id, slot)
  values (p_match_id, p_first_user, 1), (p_match_id, p_second_user, 2);
  update public.player_stats set active_match_id = p_match_id
  where user_id in (p_first_user, p_second_user);
  return public.ranked_match_record(p_match_id);
end;
$$;

create or replace function public.ranked_finish_match(
  p_match_id uuid, p_server_run uuid, p_winner_user uuid, p_reason text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_match public.matches%rowtype;
  v_stat public.player_stats%rowtype;
  v_first integer;
  v_second integer;
  v_delta integer;
begin
  if char_length(coalesce(p_reason, '')) not between 1 and 100 then
    raise exception 'INVALID_REASON' using errcode = 'PT400';
  end if;
  select * into v_match from public.matches where match_id = p_match_id for update;
  if not found then raise exception 'UNKNOWN_MATCH' using errcode = 'PT404'; end if;
  if v_match.server_run_id is distinct from p_server_run then
    raise exception 'RUN_MISMATCH' using errcode = 'PT409'; end if;
  if v_match.status = 'FINALIZED' then
    if v_match.winner_user_id = p_winner_user and v_match.reason = p_reason then
      return public.ranked_match_record(p_match_id);
    end if;
    raise exception 'RESULT_CONFLICT' using errcode = 'PT409';
  end if;
  if v_match.status <> 'RUNNING' then
    raise exception 'MATCH_NOT_RUNNING' using errcode = 'PT409'; end if;
  if p_winner_user is null or p_winner_user not in (v_match.first_user_id, v_match.second_user_id) then
    raise exception 'INVALID_WINNER' using errcode = 'PT400'; end if;
  -- Lock stats in the same order as begin/void. A player cannot start a new game yet.
  for v_stat in select * from public.player_stats
      where user_id in (v_match.first_user_id, v_match.second_user_id)
      order by user_id for update loop
    if v_stat.active_match_id is distinct from p_match_id then
      raise exception 'ACTIVE_MATCH_CONFLICT' using errcode = 'PT409'; end if;
    if v_stat.user_id = v_match.first_user_id then v_first := v_stat.rating;
    else v_second := v_stat.rating; end if;
  end loop;
  if v_first is null or v_second is null then
    raise exception 'MISSING_STATS' using errcode = 'PT409'; end if;
  -- Elo 1000/K32. PostgreSQL numeric round() rounds half away from zero.
  if p_winner_user = v_match.first_user_id then
    v_delta := round(32 * (1 - 1 / (1 + power(10::numeric, (v_second - v_first) / 400.0))));
  else
    v_delta := round(32 * (1 - 1 / (1 + power(10::numeric, (v_first - v_second) / 400.0))));
  end if;
  update public.matches set status = 'FINALIZED', winner_user_id = p_winner_user,
      reason = p_reason, ended_at = now() where match_id = p_match_id;
  update public.match_participants set outcome = case when user_id = p_winner_user then 'WIN' else 'LOSS' end,
      rating_before = case when slot = 1 then v_first else v_second end,
      rating_after = case
        when slot = 1 and user_id = p_winner_user then v_first + v_delta
        when slot = 1 then v_first - v_delta
        when user_id = p_winner_user then v_second + v_delta
        else v_second - v_delta end
    where match_id = p_match_id;
  update public.player_stats set
    rating = case when user_id = p_winner_user then rating + v_delta else rating - v_delta end,
    wins = wins + case when user_id = p_winner_user then 1 else 0 end,
    losses = losses + case when user_id = p_winner_user then 0 else 1 end,
    games = games + 1, active_match_id = null
    where user_id in (v_match.first_user_id, v_match.second_user_id);
  return public.ranked_match_record(p_match_id);
end;
$$;

create or replace function public.ranked_void_match(
  p_match_id uuid, p_server_run uuid, p_reason text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_match public.matches%rowtype;
  v_stat public.player_stats%rowtype;
begin
  if char_length(coalesce(p_reason, '')) not between 1 and 100 then
    raise exception 'INVALID_REASON' using errcode = 'PT400'; end if;
  select * into v_match from public.matches where match_id = p_match_id for update;
  if not found then raise exception 'UNKNOWN_MATCH' using errcode = 'PT404'; end if;
  if v_match.server_run_id is distinct from p_server_run then
    raise exception 'RUN_MISMATCH' using errcode = 'PT409'; end if;
  if v_match.status = 'VOID' then
    if v_match.reason = p_reason then return public.ranked_match_record(p_match_id); end if;
    raise exception 'VOID_CONFLICT' using errcode = 'PT409';
  end if;
  if v_match.status <> 'RUNNING' then
    raise exception 'MATCH_ALREADY_FINALIZED' using errcode = 'PT409'; end if;
  for v_stat in select * from public.player_stats
      where user_id in (v_match.first_user_id, v_match.second_user_id)
      order by user_id for update loop
    if v_stat.active_match_id is distinct from p_match_id then
      raise exception 'ACTIVE_MATCH_CONFLICT' using errcode = 'PT409'; end if;
  end loop;
  update public.matches set status = 'VOID', reason = p_reason, ended_at = now()
    where match_id = p_match_id;
  update public.match_participants set outcome = 'VOID' where match_id = p_match_id;
  update public.player_stats set active_match_id = null
    where user_id in (v_match.first_user_id, v_match.second_user_id);
  return public.ranked_match_record(p_match_id);
end;
$$;

create or replace function public.ranked_void_stopped_run(p_stopped_run uuid) returns integer
language plpgsql security definer set search_path = '' as $$
declare
  v_match_id uuid;
  v_match_ids uuid[];
  v_stat public.player_stats%rowtype;
  v_count integer := 0;
begin
  if p_stopped_run is null then raise exception 'INVALID_RUN' using errcode = 'PT400'; end if;
  -- Caller must positively confirm this run stopped. The row is a durable late-begin fence.
  insert into public.server_runs(run_id, state, stopped_at)
    values (p_stopped_run, 'STOPPED', now()) on conflict do nothing;
  perform 1 from public.server_runs where run_id = p_stopped_run for update;
  update public.server_runs set state = 'STOPPED', stopped_at = coalesce(stopped_at, now())
    where run_id = p_stopped_run;
  -- Lock all match rows, then all involved accounts in deterministic order before voiding.
  -- A finish takes match -> account locks; a begin takes run -> account locks.
  select array_agg(match_id order by match_id) into v_match_ids from (
    select match_id from public.matches where server_run_id = p_stopped_run
      and status = 'RUNNING' order by match_id for update
  ) locked;
  if v_match_ids is null then return 0; end if;
  for v_stat in select * from public.player_stats where user_id in (
      select first_user_id from public.matches where match_id = any(v_match_ids)
      union
      select second_user_id from public.matches where match_id = any(v_match_ids)
    ) order by user_id for update loop
    null;
  end loop;
  foreach v_match_id in array v_match_ids loop
    perform public.ranked_void_match(v_match_id, p_stopped_run, 'SERVER_STOPPED');
    v_count := v_count + 1;
  end loop;
  return v_count;
end;
$$;

create or replace function public.ranked_leaderboard()
returns table(rank integer, user_id uuid, display_name text, rating integer,
              wins integer, losses integer, games integer)
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED' using errcode = 'PT401'; end if;
  return query
    select rank() over (order by s.rating desc)::integer, s.user_id,
      p.display_name, s.rating, s.wins, s.losses, s.games
    from public.player_stats s join public.profiles p on p.user_id = s.user_id
    where s.games > 0 order by s.rating desc, s.user_id limit 100;
end;
$$;

alter table public.profiles enable row level security;
alter table public.server_runs enable row level security;
alter table public.matches enable row level security;
alter table public.match_participants enable row level security;
alter table public.player_stats enable row level security;

revoke all on public.server_runs, public.profiles, public.matches, public.match_participants, public.player_stats
  from public, anon, authenticated;
grant select (user_id, display_name) on public.profiles to authenticated;
grant update (display_name) on public.profiles to authenticated;
drop policy if exists profiles_read on public.profiles;
drop policy if exists profiles_own_update on public.profiles;
create policy profiles_read on public.profiles for select to authenticated using (true);
create policy profiles_own_update on public.profiles for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

revoke all on function public.create_ranked_profile(), public.ranked_match_record(uuid),
  public.ranked_get_match(uuid), public.ranked_begin_match(uuid,uuid,text,uuid,uuid),
  public.ranked_finish_match(uuid,uuid,uuid,text), public.ranked_void_match(uuid,uuid,text),
  public.ranked_void_stopped_run(uuid), public.ranked_leaderboard()
  from public, anon, authenticated;
grant execute on function public.ranked_get_match(uuid),
  public.ranked_begin_match(uuid,uuid,text,uuid,uuid),
  public.ranked_finish_match(uuid,uuid,uuid,text), public.ranked_void_match(uuid,uuid,text),
  public.ranked_void_stopped_run(uuid) to service_role;
grant execute on function public.ranked_leaderboard() to authenticated;
commit;
