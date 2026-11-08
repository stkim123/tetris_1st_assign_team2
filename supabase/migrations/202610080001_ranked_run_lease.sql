-- Apply after 202609290001_ranked_pvp.sql. Drain and stop the pre-lease server before
-- applying this migration. Initial admission is deliberately disabled; /admin/open
-- performs the one-time cutover. Afterwards the policy survives process restarts.
begin;

create table public.ranked_admission_control (
  singleton boolean primary key default true check (singleton),
  owner_run_id uuid,
  lease_until timestamptz,
  enabled boolean not null default false,
  check ((owner_run_id is null) = (lease_until is null))
);
insert into public.ranked_admission_control(singleton) values (true);
alter table public.ranked_admission_control enable row level security;
revoke all on public.ranked_admission_control from public, anon, authenticated;

-- Every match mutation takes this row lock before run, match and account locks.
-- A stale process therefore cannot commit a late begin or result after takeover.
create function public.ranked_assert_lease(p_run uuid, p_require_open boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare v_control public.ranked_admission_control%rowtype;
begin
  select * into v_control from public.ranked_admission_control where singleton for update;
  if p_run is null or v_control.owner_run_id is distinct from p_run
      or v_control.lease_until is null or v_control.lease_until <= clock_timestamp()
      or (p_require_open and not v_control.enabled) then
    raise exception 'RUN_NOT_OWNER' using errcode = 'PT409';
  end if;
end;
$$;

-- Internal recovery is called only with the admission row already locked. It
-- fences the old run before touching matches and does not change settled Elo.
create function public.ranked_recover_run_internal(p_stopped_run uuid) returns integer
language plpgsql security definer set search_path = '' as $$
declare
  v_match_ids uuid[];
  v_match_id uuid;
  v_stat public.player_stats%rowtype;
  v_count integer := 0;
begin
  insert into public.server_runs(run_id, state, stopped_at)
    values (p_stopped_run, 'STOPPED', now()) on conflict do nothing;
  perform 1 from public.server_runs where run_id = p_stopped_run for update;
  update public.server_runs set state = 'STOPPED', stopped_at = coalesce(stopped_at, now())
    where run_id = p_stopped_run;
  select array_agg(match_id order by match_id) into v_match_ids from (
    select match_id from public.matches where server_run_id = p_stopped_run
      and status = 'RUNNING' order by match_id for update
  ) locked;
  if v_match_ids is null then return 0; end if;
  for v_stat in select * from public.player_stats where user_id in (
      select first_user_id from public.matches where match_id = any(v_match_ids)
      union select second_user_id from public.matches where match_id = any(v_match_ids)
    ) order by user_id for update loop null; end loop;
  foreach v_match_id in array v_match_ids loop
    update public.matches set status = 'VOID', reason = 'SERVER_STOPPED', ended_at = now()
      where match_id = v_match_id and status = 'RUNNING';
    update public.match_participants set outcome = 'VOID' where match_id = v_match_id;
    update public.player_stats set active_match_id = null where active_match_id = v_match_id;
    v_count := v_count + 1;
  end loop;
  return v_count;
end;
$$;

-- One transaction elects a single owner, reconciles every previous active run,
-- and renews a 30-second lease. A process whose own lease expired is never
-- allowed to revive the same run ID; it must exit and start a fresh process.
create function public.ranked_run_lease(p_server_run uuid, p_action text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_control public.ranked_admission_control%rowtype;
  v_old uuid;
  v_run uuid;
  v_state text;
begin
  if p_server_run is null or p_action is null
      or p_action not in ('CLAIM', 'RENEW', 'OPEN', 'DRAIN') then
    raise exception 'INVALID_LEASE_REQUEST' using errcode = 'PT400';
  end if;
  select * into v_control from public.ranked_admission_control where singleton for update;
  if p_action = 'RENEW' or p_action = 'DRAIN' then
    if v_control.owner_run_id is distinct from p_server_run
        or v_control.lease_until <= clock_timestamp() then
      return jsonb_build_object('owned', false, 'enabled', v_control.enabled);
    end if;
    update public.ranked_admission_control set lease_until = clock_timestamp() + interval '30 seconds',
        enabled = case when p_action = 'DRAIN' then false else enabled end where singleton;
    return jsonb_build_object('owned', true,
      'enabled', case when p_action = 'DRAIN' then false else v_control.enabled end);
  end if;
  if v_control.owner_run_id = p_server_run then
    if v_control.lease_until <= clock_timestamp() then
      return jsonb_build_object('owned', false, 'enabled', v_control.enabled);
    end if;
    update public.ranked_admission_control set lease_until = clock_timestamp() + interval '30 seconds',
        enabled = case when p_action = 'OPEN' then true else enabled end where singleton;
    return jsonb_build_object('owned', true,
      'enabled', case when p_action = 'OPEN' then true else v_control.enabled end);
  end if;
  if v_control.owner_run_id is not null and v_control.lease_until > clock_timestamp() then
    return jsonb_build_object('owned', false, 'enabled', v_control.enabled);
  end if;
  if p_action = 'CLAIM' and not v_control.enabled then
    return jsonb_build_object('owned', false, 'enabled', false);
  end if;
  select state into v_state from public.server_runs where run_id = p_server_run for update;
  if v_state = 'STOPPED' then
    raise exception 'RUN_STOPPED' using errcode = 'PT409';
  end if;
  -- Fencing and recovery happen before assigning ownership. Existing clients
  -- cannot create new matches while this transaction holds the singleton lock.
  for v_run in select run_id from public.server_runs
      where state = 'ACTIVE' and run_id <> p_server_run order by run_id loop
    perform public.ranked_recover_run_internal(v_run);
  end loop;
  v_old := v_control.owner_run_id;
  if v_old is not null and v_old <> p_server_run then
    perform public.ranked_recover_run_internal(v_old);
  end if;
  insert into public.server_runs(run_id, state) values (p_server_run, 'ACTIVE')
    on conflict do nothing;
  update public.ranked_admission_control set owner_run_id = p_server_run,
      lease_until = clock_timestamp() + interval '30 seconds',
      enabled = case when p_action = 'OPEN' then true else enabled end where singleton;
  return jsonb_build_object('owned', true,
    'enabled', case when p_action = 'OPEN' then true else v_control.enabled end);
end;
$$;

-- The three match functions from the previous migration are replaced below
-- with versions that assert the singleton lease before their original logic.

create or replace function public.ranked_begin_match(
  p_match_id uuid, p_server_run uuid, p_rules_version text, p_first_user uuid, p_second_user uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_match public.matches%rowtype;
  v_stat public.player_stats%rowtype;
  v_run_state text;
  v_count integer := 0;
begin
  perform public.ranked_assert_lease(p_server_run, true);
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
  perform public.ranked_assert_lease(p_server_run, false);
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
  perform public.ranked_assert_lease(p_server_run, false);
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


-- The legacy one-argument recovery RPC is intentionally no longer callable by
-- service_role. Manual recovery now proves ownership of the current lease.
create function public.ranked_void_stopped_run(p_current_run uuid, p_stopped_run uuid)
returns integer language plpgsql security definer set search_path = '' as $$
begin
  if p_stopped_run is null or p_stopped_run = p_current_run then
    raise exception 'INVALID_RUN' using errcode = 'PT400';
  end if;
  perform public.ranked_assert_lease(p_current_run, false);
  return public.ranked_recover_run_internal(p_stopped_run);
end;
$$;

revoke all on function public.ranked_void_stopped_run(uuid) from public, anon, authenticated,
  service_role;
revoke all on function public.ranked_assert_lease(uuid,boolean),
  public.ranked_recover_run_internal(uuid), public.ranked_run_lease(uuid,text),
  public.ranked_void_stopped_run(uuid,uuid) from public, anon, authenticated;
grant execute on function public.ranked_run_lease(uuid,text),
  public.ranked_void_stopped_run(uuid,uuid) to service_role;

commit;

