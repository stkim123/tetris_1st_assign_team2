-- Run after bootstrap.sql and the migration, in a fresh isolated database.
set role supabase_auth_admin;
do $$ begin
  if public.before_username_user_created('{"user":{"email":"student_01@players.campus-quest.invalid","app_metadata":{"provider":"email"}}}'::jsonb) <> '{}'::jsonb then
    raise exception 'username signup denied'; end if;
  if public.before_username_user_created('{"user":{"email":"user@example.com","app_metadata":{"provider":"email"}}}'::jsonb)->'error'->>'http_code' <> '403' then
    raise exception 'email signup accepted'; end if;
  if public.before_username_user_created('{"user":{"email":"student_01@players.campus-quest.invalid","app_metadata":{"provider":"google"}}}'::jsonb)->'error'->>'http_code' <> '403' then
    raise exception 'non-email signup accepted'; end if;
end $$;
reset role;

insert into auth.users(id, email) values
 ('00000000-0000-4000-8000-000000000001', 'student_01@players.campus-quest.invalid'),
 ('00000000-0000-4000-8000-000000000002', null),
 ('00000000-0000-4000-8000-000000000003', null),
 ('00000000-0000-4000-8000-000000000004', null);

do $$ begin
  if (select count(*) from public.player_stats) <> 4 then
    raise exception 'profile/stats signup trigger failed'; end if;
  if (select display_name from public.profiles
      where user_id = '00000000-0000-4000-8000-000000000001') <> 'student_01' then
    raise exception 'username profile name not derived from Auth identity'; end if;
end $$;

do $$ begin
  begin
    update auth.users set email = 'other@players.campus-quest.invalid'
      where id = '00000000-0000-4000-8000-000000000001';
    raise exception 'username changed email';
  exception when check_violation then null; end;
  begin
    update auth.users set email = 'other@players.campus-quest.invalid'
      where id = '00000000-0000-4000-8000-000000000002';
    raise exception 'non-username reserved the namespace';
  exception when check_violation then null; end;
end $$;

set role authenticated;
do $$ begin
  begin
    perform public.ranked_begin_match('10000000-0000-4000-8000-000000000001',
      '20000000-0000-4000-8000-000000000001', 'v1',
      '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002');
    raise exception 'client called server-only begin RPC';
  exception when insufficient_privilege then null; end;
  begin
    update public.player_stats set rating = 9999;
    raise exception 'client edited rating';
  exception when insufficient_privilege then null; end;
  begin
    update public.profiles set display_name = 'forged';
    raise exception 'client edited ranked username';
  exception when insufficient_privilege then null; end;
  begin
    update public.server_runs set state = 'ACTIVE';
    raise exception 'client reopened a stopped run';
  exception when insufficient_privilege then null; end;
end $$;

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', false);
do $$ begin
  if (select count(*) from public.ranked_leaderboard()) <> 0 then
    raise exception 'unplayed user shown in leaderboard'; end if;
end $$;
reset role;

set role service_role;
do $$
declare v jsonb;
begin
  v := public.ranked_begin_match('10000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000001', 'v1',
    '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002');
  if v->>'status' <> 'RUNNING' then raise exception 'begin failed'; end if;
  v := public.ranked_begin_match('10000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000001', 'v1',
    '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002');
  if v->>'status' <> 'RUNNING' then raise exception 'begin retry failed'; end if;
  begin
    perform public.ranked_begin_match('10000000-0000-4000-8000-000000000002',
      '20000000-0000-4000-8000-000000000001', 'v1',
      '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000003');
    raise exception 'active account accepted another match';
  exception when sqlstate 'PT409' then null; end;
  begin
    perform public.ranked_finish_match('10000000-0000-4000-8000-000000000001',
      '20000000-0000-4000-8000-000000000002',
      '00000000-0000-4000-8000-000000000001', 'WIN');
    raise exception 'different server run finished match';
  exception when sqlstate 'PT409' then null; end;
  begin
    perform public.ranked_finish_match('10000000-0000-4000-8000-000000000001',
      null, '00000000-0000-4000-8000-000000000001', 'WIN');
    raise exception 'null server run finished match';
  exception when sqlstate 'PT409' then null; end;
  begin
    perform public.ranked_finish_match('10000000-0000-4000-8000-000000000001',
      '20000000-0000-4000-8000-000000000001', null, 'WIN');
    raise exception 'null winner finished match';
  exception when sqlstate 'PT400' then null; end;
  v := public.ranked_finish_match('10000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000001',
    '00000000-0000-4000-8000-000000000001', 'WIN');
  if v->>'status' <> 'FINALIZED' or (v->>'first_rating_after')::int <> 1016
      or (v->>'second_rating_after')::int <> 984 then
    raise exception 'Elo result not atomic'; end if;
  perform public.ranked_finish_match('10000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000001',
    '00000000-0000-4000-8000-000000000001', 'WIN');
  begin
    perform public.ranked_finish_match('10000000-0000-4000-8000-000000000001',
      '20000000-0000-4000-8000-000000000001',
      '00000000-0000-4000-8000-000000000002', 'WIN');
    raise exception 'conflicting result accepted';
  exception when sqlstate 'PT409' then null; end;
  begin
    perform public.ranked_void_match('10000000-0000-4000-8000-000000000001',
      '20000000-0000-4000-8000-000000000001', 'ERROR');
    raise exception 'finalized result voided';
  exception when sqlstate 'PT409' then null; end;
  v := public.ranked_begin_match('10000000-0000-4000-8000-000000000003',
    '20000000-0000-4000-8000-000000000001', 'v1',
    '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000003');
  v := public.ranked_void_match('10000000-0000-4000-8000-000000000003',
    '20000000-0000-4000-8000-000000000001', 'CANCELLED');
  if v->>'status' <> 'VOID' then raise exception 'void failed'; end if;
  perform public.ranked_void_match('10000000-0000-4000-8000-000000000003',
    '20000000-0000-4000-8000-000000000001', 'CANCELLED');
  v := public.ranked_begin_match('10000000-0000-4000-8000-000000000004',
    '20000000-0000-4000-8000-000000000002', 'v1',
    '00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000004');
  if public.ranked_void_stopped_run('20000000-0000-4000-8000-000000000002') <> 1 then
    raise exception 'stopped run recovery count'; end if;
  if public.ranked_void_stopped_run('20000000-0000-4000-8000-000000000002') <> 0 then
    raise exception 'stopped run recovery not idempotent'; end if;
  begin
    perform public.ranked_begin_match('10000000-0000-4000-8000-000000000006',
      '20000000-0000-4000-8000-000000000002', 'v1',
      '00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000004');
    raise exception 'stopped run accepted late begin';
  exception when sqlstate 'PT409' then null; end;
  if public.ranked_void_stopped_run('20000000-0000-4000-8000-000000000003') <> 0 then
    raise exception 'absent run recovery count'; end if;
  begin
    perform public.ranked_begin_match('10000000-0000-4000-8000-000000000007',
      '20000000-0000-4000-8000-000000000003', 'v1',
      '00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000004');
    raise exception 'absent stopped run accepted late begin';
  exception when sqlstate 'PT409' then null; end;
end $$;
reset role;

do $$ begin
  if (select status from public.matches where match_id =
      '10000000-0000-4000-8000-000000000001') <> 'FINALIZED' then
    raise exception 'recovery changed finalized result'; end if;
  if (select state from public.server_runs where run_id =
      '20000000-0000-4000-8000-000000000002') <> 'STOPPED' then
    raise exception 'run fence not persisted'; end if;
  if (select games from public.player_stats where user_id =
      '00000000-0000-4000-8000-000000000001') <> 1 then
    raise exception 'retry counted twice'; end if;
  if (select games from public.player_stats where user_id =
      '00000000-0000-4000-8000-000000000003') <> 0 then
    raise exception 'void changed rating stats'; end if;
end $$;

set role service_role;
do $$ begin
  perform public.ranked_begin_match('10000000-0000-4000-8000-000000000005',
    '20000000-0000-4000-8000-000000000001', 'v1',
    '00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000004');
  perform public.ranked_finish_match('10000000-0000-4000-8000-000000000005',
    '20000000-0000-4000-8000-000000000001',
    '00000000-0000-4000-8000-000000000003', 'WIN');
end $$;
reset role;

set role authenticated;
do $$
declare v_ids uuid[];
begin
  if (select count(*) from public.ranked_leaderboard()) <> 4 then
    raise exception 'leaderboard games filter'; end if;
  if (select count(*) from public.ranked_leaderboard() where rank = 1 and rating = 1016) <> 2 then
    raise exception 'shared first place rank'; end if;
  if (select count(*) from public.ranked_leaderboard() where rank = 3 and rating = 984) <> 2 then
    raise exception 'shared third place rank'; end if;
  select array_agg(user_id) into v_ids from public.ranked_leaderboard();
  if v_ids is distinct from array[
      '00000000-0000-4000-8000-000000000001'::uuid,
      '00000000-0000-4000-8000-000000000003'::uuid,
      '00000000-0000-4000-8000-000000000002'::uuid,
      '00000000-0000-4000-8000-000000000004'::uuid] then
    raise exception 'leaderboard tie order changed'; end if;
end $$;
reset role;

select 'PASS ranked SQL regression' as result;
